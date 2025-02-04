package deciders;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Random;

import evaluators.Evaluator;
import games.GameState;
import players.Player;

/**
 * A decider that chooses moves to play at random, based on what moves are
 * legal in the current games state.
 */
public class RandomDecider extends Decider {

	/**
	 * Constructor.
	 */
	public RandomDecider() {}

	@Override
	public String getType() {
		return "Random";
	}
	
	@Override
	public String toFileString() {
		return "Random(" + ")";
	}

	@Override
	public Point decide(GameState game, Evaluator e, Player p, int maxSearchTime) {
		// Creates a list of all legal moves.
		boolean[][] legalMoves = game.getLegalMoves(p);
		ArrayList<Point> possibleMoves = new ArrayList<Point>();
		for (int row = 0; row < game.getBoardDims()[0]; ++row) {
			for (int col = 0; col < game.getBoardDims()[1]; ++col) {
				if (legalMoves[row][col]) {
					possibleMoves.add(new Point(row, col));
				}
			}
		}

		// Gets a random move from the legal moves.
		Point theMove = possibleMoves.get(new Random().nextInt(possibleMoves.size()));

		// Sets the player's output.
		p.setOutput("Move chosen: (" + theMove.x + "," + theMove.y + ").");

		// Returns the move.
		return theMove;
	}

}
