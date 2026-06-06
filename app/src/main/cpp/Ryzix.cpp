// ─────────────────────────────────────────────────────────────────────────────
// Ryzix.cpp  —  A1Chess built-in chess engine  (~1000 ELO target)
// Protocol  : UCI (Universal Chess Interface)
// Build     : single translation unit, no external dependencies
// Strength  : depth-4 negamax + alpha-beta + MVV-LVA + piece-square tables
// ─────────────────────────────────────────────────────────────────────────────
#include <algorithm>
#include <chrono>
#include <climits>
#include <cstring>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
using namespace std;

// ── Piece codes ───────────────────────────────────────────────────────────────
// 0=empty  +1..+6=White P N B R Q K  -1..-6=Black P N B R Q K
enum Pc { P=1, N=2, B=3, R=4, Q=5, K=6 };
static const int MAT[7] = { 0, 100, 320, 330, 500, 900, 20000 };

// ── Piece-square tables (white's view: a1=idx 0, h8=idx 63) ─────────────────
static const int8_t PST[6][64] = {
{ // Pawn
  0,  0,  0,  0,  0,  0,  0,  0,
 50, 50, 50, 50, 50, 50, 50, 50,
 10, 10, 20, 30, 30, 20, 10, 10,
  5,  5, 10, 25, 25, 10,  5,  5,
  0,  0,  0, 20, 20,  0,  0,  0,
  5, -5,-10,  0,  0,-10, -5,  5,
  5, 10, 10,-20,-20, 10, 10,  5,
  0,  0,  0,  0,  0,  0,  0,  0
},{// Knight
-50,-40,-30,-30,-30,-30,-40,-50,
-40,-20,  0,  0,  0,  0,-20,-40,
-30,  0, 10, 15, 15, 10,  0,-30,
-30,  5, 15, 20, 20, 15,  5,-30,
-30,  0, 15, 20, 20, 15,  0,-30,
-30,  5, 10, 15, 15, 10,  5,-30,
-40,-20,  0,  5,  5,  0,-20,-40,
-50,-40,-30,-30,-30,-30,-40,-50
},{// Bishop
-20,-10,-10,-10,-10,-10,-10,-20,
-10,  0,  0,  0,  0,  0,  0,-10,
-10,  0,  5, 10, 10,  5,  0,-10,
-10,  5,  5, 10, 10,  5,  5,-10,
-10,  0, 10, 10, 10, 10,  0,-10,
-10, 10, 10, 10, 10, 10, 10,-10,
-10,  5,  0,  0,  0,  0,  5,-10,
-20,-10,-10,-10,-10,-10,-10,-20
},{// Rook
  0,  0,  0,  0,  0,  0,  0,  0,
  5, 10, 10, 10, 10, 10, 10,  5,
 -5,  0,  0,  0,  0,  0,  0, -5,
 -5,  0,  0,  0,  0,  0,  0, -5,
 -5,  0,  0,  0,  0,  0,  0, -5,
 -5,  0,  0,  0,  0,  0,  0, -5,
 -5,  0,  0,  0,  0,  0,  0, -5,
  0,  0,  0,  5,  5,  0,  0,  0
},{// Queen
-20,-10,-10, -5, -5,-10,-10,-20,
-10,  0,  0,  0,  0,  0,  0,-10,
-10,  0,  5,  5,  5,  5,  0,-10,
 -5,  0,  5,  5,  5,  5,  0, -5,
  0,  0,  5,  5,  5,  5,  0, -5,
-10,  5,  5,  5,  5,  5,  0,-10,
-10,  0,  5,  0,  0,  0,  0,-10,
-20,-10,-10, -5, -5,-10,-10,-20
},{// King (middlegame)
-30,-40,-40,-50,-50,-40,-40,-30,
-30,-40,-40,-50,-50,-40,-40,-30,
-30,-40,-40,-50,-50,-40,-40,-30,
-30,-40,-40,-50,-50,-40,-40,-30,
-20,-30,-30,-40,-40,-30,-30,-20,
-10,-20,-20,-20,-20,-20,-20,-10,
 20, 20,  0,  0,  0,  0, 20, 20,
 20, 30, 10,  0,  0, 10, 30, 20
}};

// ── Board globals ─────────────────────────────────────────────────────────────
static int  BD[64];    // board: pos=white, neg=black, 0=empty
static bool WTM;       // white to move
static int  EP;        // en-passant target square (-1=none)
static bool CAS[4];    // castling rights: [wK wQ bK bQ]
static int  HALF;      // half-move clock
static int  FULL;      // full-move number

static bool gStop;

// ── Move ──────────────────────────────────────────────────────────────────────
struct Move { int8_t fr, to, pro; uint8_t flags; };
// flags: 0=normal  1=en-passant  2=castling

// ── Utilities ─────────────────────────────────────────────────────────────────
static inline int  RANK(int s)      { return s >> 3; }
static inline int  FILE_(int s)     { return s & 7;  }
static inline int  SQ(int r, int f) { return (r << 3) | f; }
static inline bool OB(int r, int f) { return (unsigned)r < 8u && (unsigned)f < 8u; }
static inline int  SGN(int x)       { return (x > 0) - (x < 0); }
static inline int  ABS_(int x)      { return x < 0 ? -x : x; }

static int kingOf(int side) {
    int k = side * K;
    for (int i = 0; i < 64; i++) if (BD[i] == k) return i;
    return -1;
}

static bool attacked(int sq, int bySide) {
    int r = RANK(sq), f = FILE_(sq);
    // Pawns (bySide's pawns attack diagonally toward sq)
    int pr = bySide > 0 ? r - 1 : r + 1;
    for (int df : {-1, 1})
        if (OB(pr, f + df) && BD[SQ(pr, f + df)] == bySide * P) return true;
    // Knights
    static const int KND[8][2] = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
    for (auto& d : KND) if (OB(r+d[0], f+d[1]) && BD[SQ(r+d[0], f+d[1])] == bySide*N) return true;
    // Diagonals (bishop / queen)
    static const int DIAG[4][2] = {{1,1},{1,-1},{-1,1},{-1,-1}};
    for (auto& d : DIAG)
        for (int r2=r+d[0], f2=f+d[1]; OB(r2,f2); r2+=d[0], f2+=d[1]) {
            int p = BD[SQ(r2,f2)];
            if (p == bySide*B || p == bySide*Q) return true;
            if (p) break;
        }
    // Straights (rook / queen)
    static const int STRA[4][2] = {{1,0},{-1,0},{0,1},{0,-1}};
    for (auto& d : STRA)
        for (int r2=r+d[0], f2=f+d[1]; OB(r2,f2); r2+=d[0], f2+=d[1]) {
            int p = BD[SQ(r2,f2)];
            if (p == bySide*R || p == bySide*Q) return true;
            if (p) break;
        }
    // King
    for (int dr=-1; dr<=1; dr++) for (int df=-1; df<=1; df++)
        if ((dr||df) && OB(r+dr,f+df) && BD[SQ(r+dr,f+df)] == bySide*K) return true;
    return false;
}

static bool inCheck(int side) { int k = kingOf(side); return k >= 0 && attacked(k, -side); }

// ── Move generation ───────────────────────────────────────────────────────────
static void push(vector<Move>& ml, int fr, int to, int pro = 0, int flags = 0) {
    ml.push_back({(int8_t)fr, (int8_t)to, (int8_t)pro, (uint8_t)flags});
}

static void genMoves(vector<Move>& ml) {
    int s = WTM ? 1 : -1;
    static const int DIAG[4][2] = {{1,1},{1,-1},{-1,1},{-1,-1}};
    static const int STRA[4][2] = {{1,0},{-1,0},{0,1},{0,-1}};
    static const int KND [8][2] = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};

    for (int sq = 0; sq < 64; sq++) {
        int p = BD[sq];
        if (SGN(p) != s) continue;
        int r = RANK(sq), f = FILE_(sq), ap = ABS_(p);

        if (ap == P) {
            int fwd = s > 0 ? 1 : -1;
            int startRank = s > 0 ? 1 : 6, promoRank = s > 0 ? 6 : 1;
            int r1 = r + fwd;
            if (OB(r1, f) && !BD[SQ(r1, f)]) {
                if (r == promoRank) { for (int pp : {Q,R,B,N}) push(ml, sq, SQ(r1,f), pp); }
                else {
                    push(ml, sq, SQ(r1, f));
                    if (r == startRank && !BD[SQ(r+2*fwd, f)])
                        push(ml, sq, SQ(r+2*fwd, f));
                }
            }
            for (int df : {-1, 1}) {
                if (!OB(r1, f+df)) continue;
                int dst = SQ(r1, f+df);
                if (BD[dst] && SGN(BD[dst]) == -s) {
                    if (r == promoRank) { for (int pp : {Q,R,B,N}) push(ml, sq, dst, pp); }
                    else push(ml, sq, dst);
                }
                if (dst == EP) push(ml, sq, dst, 0, 1);
            }
        } else if (ap == N) {
            for (auto& d : KND) {
                int r2 = r+d[0], f2 = f+d[1];
                if (OB(r2,f2) && SGN(BD[SQ(r2,f2)]) != s) push(ml, sq, SQ(r2,f2));
            }
        } else {
            // Sliding pieces
            if (ap == B || ap == Q)
                for (auto& d : DIAG)
                    for (int r2=r+d[0], f2=f+d[1]; OB(r2,f2); r2+=d[0], f2+=d[1]) {
                        int dst=SQ(r2,f2);
                        if (SGN(BD[dst]) != s) push(ml, sq, dst);
                        if (BD[dst]) break;
                    }
            if (ap == R || ap == Q)
                for (auto& d : STRA)
                    for (int r2=r+d[0], f2=f+d[1]; OB(r2,f2); r2+=d[0], f2+=d[1]) {
                        int dst=SQ(r2,f2);
                        if (SGN(BD[dst]) != s) push(ml, sq, dst);
                        if (BD[dst]) break;
                    }
            if (ap == K) {
                for (int dr=-1; dr<=1; dr++) for (int df=-1; df<=1; df++) {
                    if (!dr && !df) continue;
                    int r2=r+dr, f2=f+df;
                    if (OB(r2,f2) && SGN(BD[SQ(r2,f2)]) != s) push(ml, sq, SQ(r2,f2));
                }
                // Castling (king must not be in check)
                if (!inCheck(s)) {
                    if (s > 0 && sq == 4) {
                        if (CAS[0]&&!BD[5]&&!BD[6]&&!attacked(5,-1)&&!attacked(6,-1)) push(ml,4,6,0,2);
                        if (CAS[1]&&!BD[3]&&!BD[2]&&!BD[1]&&!attacked(3,-1)&&!attacked(2,-1)) push(ml,4,2,0,2);
                    }
                    if (s < 0 && sq == 60) {
                        if (CAS[2]&&!BD[61]&&!BD[62]&&!attacked(61,1)&&!attacked(62,1)) push(ml,60,62,0,2);
                        if (CAS[3]&&!BD[59]&&!BD[58]&&!BD[57]&&!attacked(59,1)&&!attacked(58,1)) push(ml,60,58,0,2);
                    }
                }
            }
        }
    }
}

// ── Make / Unmake ─────────────────────────────────────────────────────────────
struct Undo {
    int fr, to, moved, capTo;   // moved=piece that moved, capTo=piece originally at to-sq
    int epCapSq, epCapPiece;    // en-passant: captured pawn square and piece
    int epSqOld;
    bool cas[4];
    int half;
    int rf, rt, rp;             // castling rook: from, to, piece (-1 if not castling)
};

static Undo doMove(const Move& m) {
    Undo u;
    u.fr     = m.fr;   u.to    = m.to;
    u.moved  = BD[m.fr];
    u.capTo  = BD[m.to];
    u.epCapSq = -1;    u.epCapPiece = 0;
    u.epSqOld = EP;
    memcpy(u.cas, CAS, 4);
    u.half = HALF;
    u.rf = -1;

    int s = SGN(BD[m.fr]);
    BD[m.to]  = BD[m.fr];
    BD[m.fr]  = 0;
    HALF++;
    if (ABS_(u.moved) == P || u.capTo) HALF = 0;
    EP = -1;

    if (m.flags == 1) {
        // En passant: captured pawn is NOT on m.to but one rank behind it
        int capSq = m.to - (s > 0 ? 8 : -8);
        u.epCapSq    = capSq;
        u.epCapPiece = BD[capSq];
        BD[capSq]    = 0;
        BD[m.to]     = BD[m.to]; // already moved above
    }
    if (m.flags == 2) {
        // Castling: move the rook
        if      (m.to == 6)  { u.rf=7;  u.rt=5;  u.rp=BD[7]; }
        else if (m.to == 2)  { u.rf=0;  u.rt=3;  u.rp=BD[0]; }
        else if (m.to == 62) { u.rf=63; u.rt=61; u.rp=BD[63]; }
        else                 { u.rf=56; u.rt=59; u.rp=BD[56]; }
        BD[u.rt] = u.rp;
        BD[u.rf] = 0;
    }
    if (m.pro) BD[m.to] = s * m.pro;

    // New EP square (pawn double-push)
    if (ABS_(u.moved) == P && ABS_(m.to - m.fr) == 16)
        EP = (m.fr + m.to) / 2;

    // Update castling rights
    auto loseRight = [&](int sq) {
        if (sq == 0)  CAS[1] = false;
        if (sq == 7)  CAS[0] = false;
        if (sq == 56) CAS[3] = false;
        if (sq == 63) CAS[2] = false;
    };
    loseRight(m.fr); loseRight(m.to);
    if (m.fr == 4)  { CAS[0] = CAS[1] = false; }
    if (m.fr == 60) { CAS[2] = CAS[3] = false; }

    WTM = !WTM;
    return u;
}

static void undoMove(const Undo& u, const Move& m) {
    WTM = !WTM;
    BD[u.fr] = u.moved;
    BD[u.to] = (m.flags == 1) ? 0 : u.capTo;   // ep: to-square was empty before move
    if (u.epCapSq >= 0) BD[u.epCapSq] = u.epCapPiece;
    if (u.rf >= 0) { BD[u.rf] = u.rp; BD[u.rt] = 0; }
    EP = u.epSqOld;
    memcpy(CAS, u.cas, 4);
    HALF = u.half;
}

// ── Evaluation ────────────────────────────────────────────────────────────────
static int evaluate() {
    int score = 0;
    for (int i = 0; i < 64; i++) {
        int p = BD[i]; if (!p) continue;
        int s = SGN(p), ap = ABS_(p);
        int psi = s > 0 ? i : (56 ^ i);   // flip rank-index for black
        score += s * (MAT[ap] + PST[ap-1][psi]);
    }
    return WTM ? score : -score;   // always from side-to-move perspective
}

// ── Move ordering heuristic ────────────────────────────────────────────────────
static int mvScore(const Move& m) {
    int cap = (m.flags == 1) ? P : ABS_(BD[m.to]);   // ep always captures a pawn
    int v = cap ? 10 * MAT[cap] - MAT[ABS_(BD[m.fr])] : 0;
    if (m.pro) v += MAT[m.pro];
    return v;
}

// ── Negamax alpha-beta ────────────────────────────────────────────────────────
static Move gBest;
static int  gRootDepth;

static int negamax(int depth, int alpha, int beta) {
    if (gStop) return 0;

    vector<Move> ml;
    genMoves(ml);

    // Filter illegal moves (leaving own king in check)
    int s = WTM ? 1 : -1;
    vector<Move> legal;
    legal.reserve(ml.size());
    for (auto& mv : ml) {
        auto u = doMove(mv);
        if (!inCheck(s)) legal.push_back(mv);
        undoMove(u, mv);
    }

    if (legal.empty()) {
        if (inCheck(s)) return -(19000 + depth);   // checkmate (prefer faster mates)
        return 0;                                    // stalemate
    }
    if (depth == 0) return evaluate();

    // Sort: captures / promotions first (MVV-LVA)
    sort(legal.begin(), legal.end(), [](const Move& a, const Move& b) {
        return mvScore(a) > mvScore(b);
    });

    int best = INT_MIN / 2;
    for (auto& mv : legal) {
        auto u = doMove(mv);
        int score = -negamax(depth - 1, -beta, -alpha);
        undoMove(u, mv);
        if (gStop) break;
        if (score > best) {
            best = score;
            if (depth == gRootDepth) gBest = mv;
        }
        if (score > alpha) alpha = score;
        if (alpha >= beta) break;
    }
    return best;
}

// ── Square / move helpers ─────────────────────────────────────────────────────
static int sqFromAlg(const string& s) {
    if (s.size() < 2) return -1;
    int f = s[0] - 'a', r = s[1] - '1';
    return (f < 0 || f > 7 || r < 0 || r > 7) ? -1 : SQ(r, f);
}

static string sqToAlg(int sq) {
    string s; s += (char)('a' + FILE_(sq)); s += (char)('1' + RANK(sq)); return s;
}

static string moveToUci(const Move& m) {
    static const char PROCH[] = "xpnbrq";   // index 0..5; pro=2(N),3(B),4(R),5(Q)
    string s = sqToAlg(m.fr) + sqToAlg(m.to);
    if (m.pro) s += PROCH[m.pro];
    return s;
}

// ── Position setup ────────────────────────────────────────────────────────────
static void setStartPos() {
    memset(BD, 0, sizeof(BD));
    BD[0]=R; BD[1]=N; BD[2]=B; BD[3]=Q; BD[4]=K; BD[5]=B; BD[6]=N; BD[7]=R;
    for (int i = 8;  i < 16; i++) BD[i] =  P;
    for (int i = 48; i < 56; i++) BD[i] = -P;
    BD[56]=-R; BD[57]=-N; BD[58]=-B; BD[59]=-Q; BD[60]=-K; BD[61]=-B; BD[62]=-N; BD[63]=-R;
    WTM=true; EP=-1; CAS[0]=CAS[1]=CAS[2]=CAS[3]=true; HALF=0; FULL=1;
}

static void parseFen(const string& fen) {
    memset(BD, 0, sizeof(BD));
    CAS[0]=CAS[1]=CAS[2]=CAS[3]=false;
    EP=-1; HALF=0; FULL=1;
    istringstream ss(fen);
    string pcs, side, cas, ep, halfS, fullS;
    ss >> pcs >> side >> cas >> ep >> halfS >> fullS;
    // Piece placement: FEN rank 8 first (rank 7 in 0-indexed)
    int r = 7, f = 0;
    for (char c : pcs) {
        if (c == '/') { r--; f = 0; }
        else if (c >= '1' && c <= '8') f += c - '0';
        else {
            int p = 0;
            switch (c) {
                case 'P':p= P;break; case 'N':p= N;break; case 'B':p= B;break;
                case 'R':p= R;break; case 'Q':p= Q;break; case 'K':p= K;break;
                case 'p':p=-P;break; case 'n':p=-N;break; case 'b':p=-B;break;
                case 'r':p=-R;break; case 'q':p=-Q;break; case 'k':p=-K;break;
            }
            BD[SQ(r, f++)] = p;
        }
    }
    WTM = (side == "w");
    for (char c : cas) {
        if (c=='K') CAS[0]=true; if (c=='Q') CAS[1]=true;
        if (c=='k') CAS[2]=true; if (c=='q') CAS[3]=true;
    }
    if (ep != "-") EP = sqFromAlg(ep);
    if (!halfS.empty()) HALF = stoi(halfS);
    if (!fullS.empty()) FULL = stoi(fullS);
}

static void applyUciMove(const string& uci) {
    if (uci.size() < 4) return;
    int fr = sqFromAlg(uci.substr(0, 2));
    int to = sqFromAlg(uci.substr(2, 2));
    if (fr < 0 || to < 0) return;
    char pc = uci.size() > 4 ? uci[4] : 0;
    int pro = 0;
    switch (pc) { case 'q':pro=Q;break; case 'r':pro=R;break; case 'b':pro=B;break; case 'n':pro=N;break; }
    int flags = 0;
    if (ABS_(BD[fr]) == P && to == EP) flags = 1;              // en passant
    if (ABS_(BD[fr]) == K && ABS_(to - fr) == 2) flags = 2;   // castling
    Move m = {(int8_t)fr, (int8_t)to, (int8_t)pro, (uint8_t)flags};
    doMove(m);
}

// ── UCI command handlers ──────────────────────────────────────────────────────
static long long nowMs() {
    return chrono::duration_cast<chrono::milliseconds>(
        chrono::steady_clock::now().time_since_epoch()).count();
}

static void handlePosition(const string& line) {
    istringstream ss(line);
    string tok; ss >> tok;  // "position"
    ss >> tok;              // "startpos" or "fen"
    if (tok == "startpos") {
        setStartPos();
        ss >> tok;          // "moves" or eof
    } else {
        // Read FEN tokens until "moves" or eof
        string fen;
        while (ss >> tok) {
            if (tok == "moves") break;
            if (!fen.empty()) fen += ' ';
            fen += tok;
        }
        parseFen(fen);
    }
    if (tok == "moves") {
        string mv;
        while (ss >> mv) applyUciMove(mv);
    }
}

static void handleGo(const string& line) {
    gStop = false;
    int maxDepth = 4;
    long long timeLimitMs = 0;
    long long startMs = nowMs();

    istringstream ss(line);
    string tok; ss >> tok;  // "go"
    long long wtime=-1, btime=-1, winc=0, binc=0;
    bool hasMovetime = false;
    while (ss >> tok) {
        long long v = 0;
        if      (tok == "movetime" && (ss >> v)) { timeLimitMs = v > 50 ? v - 50 : 10; hasMovetime = true; }
        else if (tok == "depth"    && (ss >> v)) { maxDepth = (int)v; }
        else if (tok == "wtime"    && (ss >> v)) { wtime = v; }
        else if (tok == "btime"    && (ss >> v)) { btime = v; }
        else if (tok == "winc"     && (ss >> v)) { winc = v; }
        else if (tok == "binc"     && (ss >> v)) { binc = v; }
    }
    // Derive search time from game clock when movetime not given
    if (!hasMovetime && (wtime > 0 || btime > 0)) {
        long long myTime = WTM ? wtime : btime;
        long long myInc  = WTM ? winc  : binc;
        if (myTime > 0) timeLimitMs = max(50LL, myTime / 30 + myInc - 50);
    }

    // Iterative deepening: depth 1 → maxDepth
    gBest = {0, 0, 0, 0};
    for (int d = 1; d <= maxDepth && !gStop; d++) {
        gRootDepth = d;
        Move prev = gBest;
        int score = negamax(d, -30000, 30000);
        if (gStop) { gBest = prev; break; }

        // Score from white's perspective for the info line
        int cpWhite = WTM ? score : -score;
        cout << "info depth " << d
             << " score cp " << cpWhite
             << " pv " << moveToUci(gBest) << "\n";
        cout.flush();

        // Check time limit after each iteration
        if (timeLimitMs > 0 && (nowMs() - startMs) >= timeLimitMs) break;
    }

    // Fallback: if search never completed a full iteration, pick first legal move
    if (gBest.fr == 0 && gBest.to == 0) {
        vector<Move> ml; genMoves(ml);
        int s = WTM ? 1 : -1;
        for (auto& mv : ml) {
            auto u = doMove(mv);
            bool legal = !inCheck(s);
            undoMove(u, mv);
            if (legal) { gBest = mv; break; }
        }
    }

    cout << "bestmove " << moveToUci(gBest) << "\n";
    cout.flush();
}

// ── Main UCI loop ─────────────────────────────────────────────────────────────
int main() {
    ios_base::sync_with_stdio(false);
    cin.tie(nullptr);
    setStartPos();
    gStop = false;

    string line;
    while (getline(cin, line)) {
        if (line.empty()) continue;
        istringstream ss(line); string cmd; ss >> cmd;

        if (cmd == "uci") {
            cout << "id name Ryzix\n"
                 << "id author A1Chess\n"
                 << "uciok\n";
            cout.flush();
        } else if (cmd == "isready") {
            cout << "readyok\n"; cout.flush();
        } else if (cmd == "ucinewgame") {
            setStartPos();
        } else if (cmd == "position") {
            handlePosition(line);
        } else if (cmd == "go") {
            handleGo(line);
        } else if (cmd == "stop") {
            gStop = true;
        } else if (cmd == "quit" || cmd == "exit") {
            break;
        }
    }
    return 0;
}
