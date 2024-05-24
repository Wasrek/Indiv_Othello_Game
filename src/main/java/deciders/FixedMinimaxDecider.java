package deciders;

import java.awt.Point;

import evaluators.Evaluator;
import games.GameState;
import players.Player;
//
///**
// * Minimax player that searches directly to a certain depth. Can be cut off due to time constraints.
// */
//public class FixedMinimaxDecider extends MinimaxDecider {
//
//	public FixedMinimaxDecider(int depthToSearchTo) {super(depthToSearchTo);}
//
//	public String getType() {
//		return "FixedMinimax";
//	}
//
//	public String toFileString() {
//		return "FixedMinimax(" + this.depthToSearchTo + ")";
//	}
//
//	/**
//	 * Evaluates all children of the current games state and returns the most promising one.
//	 */
//	public Point getMaxMove(GameState current, int depth, long startTimestamp, int timeLimit, Evaluator e, Player p) {
//
//		// Resetting debug field for analysing the number of nodes checked.
//		this.debugNodesChecked = 0;
//
//		// Initialising storage variables.
//		Point bestMove = null;
//		float bestScore = Float.NEGATIVE_INFINITY;
//
//		// Loop to check all board spaces.
//		for (int row = 0; row < current.getBoardDims()[0]; ++row) {
//			for (int col = 0; col < current.getBoardDims()[1]; ++col) {
//
//				// Checks if a legal move is available at this space.
//				if (current.getLegalMoves(p)[row][col]) {
//
//					// Analyses the games tree that sprouts from playing a move at the current space.
//					GameState child = current.playMove(p, new Point(row, col));
//					float childScore = getMinScore(child, depth-1, startTimestamp, timeLimit, e, p, child.getOpposingPlayer(p), Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
//
//					// Determines if the child move is the best move found so far.
//					if (childScore > bestScore) {
//						bestScore = childScore;
//						bestMove = new Point(row, col);
//					}
//
//					// Stores the score in the score HashMap.
//					++debugNodesChecked;
//
//				}
//
//			}
//		}
//
//		// Stores output.
//		p.setOutput("Move chosen: (" + bestMove.x + "," + bestMove.y + "). Score: " + bestScore + ". Time remaining: " + (System.currentTimeMillis() - startTimestamp) + "ms.");
//
//		// Returns the move found with the highest score.
//		return bestMove;
//
//	}
//
//}
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class FixedMinimaxDecider extends MinimaxDecider {

	private static final Random RANDOM = new Random();

	public FixedMinimaxDecider(int depthToSearchTo) {
		super(depthToSearchTo);
	}

	public String getType() {
		return "FixedMinimax";
	}

	public String toFileString() {
		return "FixedMinimax(" + this.depthToSearchTo + ")";
	}

	public Point getMaxMove(GameState current, int depth, long startTimestamp, int timeLimit, Evaluator e, Player p) {
		this.debugNodesChecked = 0;
		Point bestMove = null;
		float bestScore = Float.NEGATIVE_INFINITY;

		List<Point> legalMoves = getLegalMoves(current, p);
		Collections.shuffle(legalMoves, RANDOM);

		for (Point move : legalMoves) {
			GameState child = current.playMove(p, move);
			float childScore = getMinScore(child, depth - 1, startTimestamp, timeLimit, e, p, child.getOpposingPlayer(p), Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

			if (childScore > bestScore) {
				bestScore = childScore;
				bestMove = move;
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
}
