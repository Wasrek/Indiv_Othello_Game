package deciders;

import evaluators.Evaluator;
import games.GameState;
import players.Player;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixedMinimaxDeciderWithPruning extends MinimaxDecider {

    private Map<GameState, Float> transpositionTable;

    public FixedMinimaxDeciderWithPruning(int depthToSearchTo) {
        super(depthToSearchTo);
        this.transpositionTable = new HashMap<>();
    }

    @Override
    public String getType() {
        return "FixedMinimaxWithPruning";
    }

    @Override
    public String toFileString() {
        return "FixedMinimaxWithPruning(" + this.depthToSearchTo + ")";
    }

    @Override
    public Point getMaxMove(GameState current, int depth, long startTimestamp, int timeLimit, Evaluator e, Player p) {
        this.debugNodesChecked = 0;
        Point bestMove = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        float alpha = Float.NEGATIVE_INFINITY;
        float beta = Float.POSITIVE_INFINITY;

        List<Point> legalMoves = getLegalMoves(current, p);
        if (legalMoves.isEmpty()) {
            return null; // No valid moves, let the game continue
        }

        // Order the moves before evaluating
        orderMoves(current, legalMoves, p, e);

        for (Point move : legalMoves) {
            GameState child = current.playMove(p, move);
            float childScore = getMinScoreWithPruning(child, depth - 1, startTimestamp, timeLimit, e, p, child.getOpposingPlayer(p), alpha, beta);

            if (childScore > bestScore) {
                bestScore = childScore;
                bestMove = move;
            }

            alpha = Math.max(alpha, bestScore);
            if (beta <= alpha) {
                break; // Beta cutoff
            }

            ++debugNodesChecked;
        }

        p.setOutput("Move chosen: (" + bestMove.x + "," + bestMove.y + "). Score: " + bestScore + ". Time remaining: " + (System.currentTimeMillis() - startTimestamp) + "ms.");
        return bestMove;
    }

    private List<Point> getLegalMoves(GameState current, Player p) {
        List<Point> legalMoves = new ArrayList<>();
        for (int row = 0; row < current.getBoardDims()[0]; ++row) {
            for (int col = 0; col < current.getBoardDims()[1]; ++col) {
                if (current.getLegalMoves(p)[row][col]) {
                    legalMoves.add(new Point(row, col));
                }
            }
        }
        return legalMoves;
    }

    private void orderMoves(GameState current, List<Point> moves, Player p, Evaluator e) {
        // Enhanced move ordering heuristic based on both capturing pieces and evaluation function
        moves.sort(Comparator.comparingInt((Point move) -> current.playMove(p, move).getCapturedPieces(p, move).size())
                .thenComparingDouble((Point move) -> e.evaluate(current.playMove(p, move), p))
                .reversed());
    }

    private float getMinScoreWithPruning(GameState current, int depth, long startTimestamp, int timeLimit, Evaluator e, Player maxPlayer, Player minPlayer, float alpha, float beta) {
        if (depth == 0 || current.isOver()) {
            return e.evaluate(current, maxPlayer);
        }

        if (transpositionTable.containsKey(current)) {
            return transpositionTable.get(current);
        }

        float bestScore = Float.POSITIVE_INFINITY;
        List<Point> legalMoves = getLegalMoves(current, minPlayer);
        if (legalMoves.isEmpty()) {
            return e.evaluate(current, maxPlayer); // No legal moves, evaluate the current state
        }

        orderMoves(current, legalMoves, minPlayer, e);

        for (Point move : legalMoves) {
            GameState child = current.playMove(minPlayer, move);
            float childScore = getMaxScoreWithPruning(child, depth - 1, startTimestamp, timeLimit, e, maxPlayer, minPlayer, alpha, beta);

            bestScore = Math.min(bestScore, childScore);
            beta = Math.min(beta, bestScore);
            if (beta <= alpha) {
                break; // Prune remaining branches.
            }
        }

        transpositionTable.put(current, bestScore);
        return bestScore;
    }

    private float getMaxScoreWithPruning(GameState current, int depth, long startTimestamp, int timeLimit, Evaluator e, Player maxPlayer, Player minPlayer, float alpha, float beta) {
        if (depth == 0 || current.isOver()) {
            return e.evaluate(current, maxPlayer);
        }

        if (transpositionTable.containsKey(current)) {
            return transpositionTable.get(current);
        }

        float bestScore = Float.NEGATIVE_INFINITY;
        List<Point> legalMoves = getLegalMoves(current, maxPlayer);
        if (legalMoves.isEmpty()) {
            return e.evaluate(current, maxPlayer); // No legal moves, evaluate the current state
        }

        orderMoves(current, legalMoves, maxPlayer, e);

        for (Point move : legalMoves) {
            GameState child = current.playMove(maxPlayer, move);
            float childScore = getMinScoreWithPruning(child, depth - 1, startTimestamp, timeLimit, e, maxPlayer, minPlayer, alpha, beta);

            bestScore = Math.max(bestScore, childScore);
            alpha = Math.max(alpha, bestScore);
            if (beta <= alpha) {
                break; // Prune remaining branches.
            }
        }

        transpositionTable.put(current, bestScore);
        return bestScore;
    }
}
