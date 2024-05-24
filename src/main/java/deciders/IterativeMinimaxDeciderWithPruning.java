package deciders;

import java.awt.Point;
import java.util.*;

import evaluators.Evaluator;
import games.GameState;
import players.Player;
import java.util.Random;

/**
 * A Minimax decider that iteratively increases the depth it searches to up to a specified maximum,
 * with alpha-beta pruning and move ordering enhancements.
 */
public class IterativeMinimaxDeciderWithPruning extends MinimaxDecider {

	private Map<GameState, Float> transpositionTable;

	private static final Random RANDOM = new Random();

	public IterativeMinimaxDeciderWithPruning(int depthToSearchTo) {
		super(depthToSearchTo);
		this.transpositionTable = new HashMap<>();
	}

	public String getType() {
		return "IterativePruning";
	}

	public String toFileString() {
		return "IterativeMinimaxWithPruning(" + this.depthToSearchTo + ")";
	}


	/**
	 * Evaluates all children of the current games state and returns the most promising one.
	 */
	public Point getMaxMove(GameState current, int maxDepth, long startTimestamp, int timeLimit, Evaluator e, Player p) {
		// Resetting debug field for analysing the number of nodes checked.
		this.debugNodesChecked = 0;

		// Initialising storage variables.
		HashMap<Point, Float> moveScores = new HashMap<>();
		int currentDepth = 1;

		// Runs a loop to analyse each move from the current games state.
		while (System.currentTimeMillis() - startTimestamp <= timeLimit && currentDepth <= maxDepth) {

			// Shuffle legal moves before iterating
			List<Point> legalMoves = getLegalMoves(current, p);
			Collections.shuffle(legalMoves, RANDOM);

			// Loop to check all board spaces.
			for (Point move : legalMoves) {
				// Checks if a legal move is available at this space.
				if (current.getLegalMoves(p)[move.x][move.y]) {

					// Analyses the games tree that sprouts from playing a move at the current space.
					GameState child = current.playMove(p, move);
					float childScore = getMinScoreWithPruning(child, currentDepth - 1, startTimestamp, timeLimit, e, p, child.getOpposingPlayer(p), Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

					// Stores the score in the score HashMap, if the time limit has not been exceeded.
					// This is so that any move placed in the HashMap has been fully evaluated.
					if (System.currentTimeMillis() - startTimestamp <= timeLimit) {
						moveScores.put(move, childScore);
						++debugNodesChecked;
					}
				}
			}

			// Increases the depth to explore to.
			++currentDepth;

		}

		// Returns the move found with the highest score.
		Point highScoringMove = null;
		for (Point key : moveScores.keySet()) {
			if (highScoringMove == null || moveScores.get(key) > moveScores.get(highScoringMove)) {
				highScoringMove = key;
			}
		}

		// Setting output message.
		p.setOutput("Move chosen: (" + highScoringMove.x + "," + highScoringMove.y + "). Score: " + moveScores.get(highScoringMove) + ". Depth reached: " + (currentDepth - 1) + ". (N/ms:- " + (this.debugNodesChecked / (System.currentTimeMillis() - startTimestamp + 1)) + "). Time remaining: " + (System.currentTimeMillis() - startTimestamp) + "ms.");

		return highScoringMove;
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