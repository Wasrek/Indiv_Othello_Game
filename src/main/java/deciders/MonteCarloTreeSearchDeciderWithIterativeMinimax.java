package deciders;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import evaluators.Evaluator;
import games.GameState;
import players.Player;
/**
 * This class implements an enhanced Monte Carlo Tree Search (MCTS) algorithm with iterative minimax and various optimizations.
 */
public class MonteCarloTreeSearchDeciderWithIterativeMinimax extends IterativeMinimaxDecider {

    // Fields for storing the search parameters.
    private boolean hardPlayouts;
    private boolean useMaxSims;
    private int maxSims;
    private double randomChance;
    private int timePerMinimax;

    // Statistical field(s).
    private int simulationsRun = 0;

    public MonteCarloTreeSearchDeciderWithIterativeMinimax(boolean useMaxSims, int maxSims, boolean useMinimax, int minimaxDepth, double randomChance, int timePerMinimax) {
        super(minimaxDepth);
        this.useMaxSims = useMaxSims;
        this.maxSims = maxSims;
        this.hardPlayouts = useMinimax;
        this.randomChance = randomChance;
        this.timePerMinimax = timePerMinimax;
    }

    public String getType() {
        return "MCTSWithIterativeMinimax";
    }

    public String toFileString() {
        return "MCTSWithIterativeMinimax(" + this.useMaxSims + "," + this.maxSims + "," + this.hardPlayouts + "," + this.depthToSearchTo + "," + this.randomChance + "," + this.timePerMinimax + ")";
    }

    /**
     * Runs the entire MCTS process and returns the best move from it.
     */
    public Point decide(GameState game, Evaluator e, Player p, int maxSearchTime) {

        // Checks if a decision needs to be made, or if the only move can be returned.
        Point foundMove = null;
        boolean onlyOneMove = true;
        for (int row = 0; row < game.getBoardDims()[0]; row++) {
            for (int col = 0; col < game.getBoardDims()[1]; col++) {
                if (game.getLegalMoves(p)[row][col]) {
                    if (foundMove == null) {
                        foundMove = new Point(row, col);
                    } else {
                        onlyOneMove = false;
                    }
                }
            }
        }
        if (onlyOneMove) {
            p.setOutput("Move chosen: (" + foundMove.x + "," + foundMove.y + "), only move available.");
            return foundMove;
        }

        simulationsRun = 0;
        Player playerIAm = p;

        // Storing start time.
        long startTime = System.currentTimeMillis();

        // Get the tree.
        TreeNode root = constructTree(game, playerIAm, e, startTime, maxSearchTime);

        // Determines the best move found.
        TreeNode bestChild = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        float bestChildEval = Float.NEGATIVE_INFINITY;
        for (TreeNode rootChild : root.children) {
            GameState rootChildGameState = game.playMove(playerIAm, rootChild.moveMade);
            float rootChildEvaluation = e.evaluate(rootChildGameState, playerIAm);
            double rootChildScore = rootChild.getWinPercent();
            if (rootChildScore > bestScore || bestChild == null) {
                bestScore = rootChildScore;
                bestChild = rootChild;
                bestChildEval = rootChildEvaluation;
            } else if (rootChildScore == bestScore && rootChildEvaluation > bestChildEval) {
                bestScore = rootChildScore;
                bestChild = rootChild;
                bestChildEval = rootChildEvaluation;
            }
        }

        // Check if bestChild is null
        if (bestChild == null) {
            // If there are no children, return the found move or choose a random move.
            System.out.println("No valid child nodes. Returning the only available move.");
            return foundMove;
        }

        // Sets output.
        p.setOutput("Move chosen: (" + bestChild.moveMade.x + "," + bestChild.moveMade.y + "). Score: " + bestChildEval + ". Win percentage: " + bestScore + "%. " + simulationsRun + " sims/" + (System.currentTimeMillis() - startTime) + " ms; " + ((double) simulationsRun / (System.currentTimeMillis() - startTime)) + " sims/ms.");

        // Returns the move.
        return bestChild.moveMade;
    }

    /**
     * Selects a move using a heuristic function as a backup strategy.
     */
    private Point selectBackupMove(GameState game, Player player, Evaluator e) {
        double bestScore = Double.NEGATIVE_INFINITY;
        Point bestMove = null;
        for (int row = 0; row < game.getBoardDims()[0]; row++) {
            for (int col = 0; col < game.getBoardDims()[1]; col++) {
                if (game.getLegalMoves(player)[row][col]) {
                    // Simulate making the move and evaluate the resulting game state
                    GameState nextState = game.playMove(player, new Point(row, col));
                    double score = e.evaluate(nextState, player);
                    // Update best move if the current move has a higher score
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new Point(row, col);
                    }
                }
            }
        }
        return bestMove;
    }

    /**
     * Method that simulates games from the given GameState, and returns whether or not the provided player won.
     */
    // Inside simulate method
    private int simulate(GameState startState, Player startPlayer, Player playerIAm, Evaluator e, long startTime, int maxSearchTime) {
        // Creating variables needed for simulation.
        GameState currentState = startState;
        Player currentPlayer = startPlayer;

        // Run the simulation loop until the game is completed.
        while (!currentState.isOver()) {
            // Checks if the player can play a move.
            if (currentState.hasLegalMoves(currentPlayer)) {
                double r = new Random(System.currentTimeMillis()).nextDouble();
                Point moveToPlay;

                // Occasionally use a RandomDecider to make an imperfect move.
                if (hardPlayouts) {
                    if (r < this.randomChance) {
                        moveToPlay = new RandomDecider().decide(currentState, e, currentPlayer, timePerMinimax);
                    } else {
                        moveToPlay = getMaxMoveIterative(currentState, currentPlayer, e, startTime, maxSearchTime);
                    }
                } else {
                    moveToPlay = new RandomDecider().decide(currentState, e, currentPlayer, timePerMinimax);
                }

                // Play the move onto the current state.
                currentState = currentState.playMove(currentPlayer, moveToPlay);
            }
            // Change to the next player.
            currentPlayer = currentState.getOpposingPlayer(currentPlayer);
        }
        // Increment debug variable.
        ++simulationsRun;
        // Determine the int to return based on if the player's score was higher than the opponent's or not.
        if (currentState.getScoreOfPlayer(playerIAm) > currentState.getScoreOfPlayer(currentState.getOpposingPlayer(playerIAm))) {
            return 1;
        } else {
            return 0;
        }
    }


    /**
     * Returns the UCT score for the provided arguments.
     */
    private double getUCTScore(int w, int n, int t) {
        final double C = Math.sqrt(2);
        return ((double) w / (double) n) + (C * Math.sqrt(Math.log(t) / (double) n));
    }

    /**
     * Internal class for storing statistics on simulations.
     */
    private class TreeNode {
        public int wins = 0;
        public int total = 0;
        public Point moveMade = null;
        public ArrayList<TreeNode> children = new ArrayList<TreeNode>();
        public GameState state;           // Added state field
        public Player currentPlayer;      // Added currentPlayer field
        public TreeNode parent;           // Added parent field

        public TreeNode(GameState state, Player currentPlayer, TreeNode parent) {
            this.state = state;
            this.currentPlayer = currentPlayer;
            this.parent = parent;
        }

        public double getWinPercent() {
            return (wins * 100) / ((double) total);
        }
    }



    /**
     * Method to perform pruning based on evaluation function.
     */
    private boolean prune(TreeNode node, Evaluator e, GameState state, Player player) {
        // Adjust pruning criteria based on evaluation function and game state
        float eval = e.evaluate(state, player);
        return eval < 0; // Example: Prune if evaluation is less than 0.
    }

    /**
     * Runs the main MCTS algorithm, which constructs the tree via numerous simulations. Returns the root
     * node of the tree.
     */

    /**
     * Select the most promising node using UCT score.
     */
    private TreeNode selectPromisingNode(TreeNode rootNode) {
        TreeNode node = rootNode;
        while (!node.children.isEmpty()) {
            node = findBestNodeWithUCT(node);
        }
        return node;
    }

    /**
     * Find the best node using UCT score.
     */
    private TreeNode findBestNodeWithUCT(TreeNode node) {
        int parentVisit = node.total;
        return node.children.stream()
                .max((node1, node2) -> Double.compare(
                        getUCTScore(node1.wins, node1.total, parentVisit),
                        getUCTScore(node2.wins, node2.total, parentVisit)
                )).orElseThrow(IllegalStateException::new);
    }

    /**
     * Expand the node by adding all possible children.
     */
    // Inside expandNode method
    private void expandNode(TreeNode node, GameState game, Player player) {
        boolean[][] legalMoves = game.getLegalMoves(player);
        for (int row = 0; row < game.getBoardDims()[0]; row++) {
            for (int col = 0; col < game.getBoardDims()[1]; col++) {
                if (legalMoves[row][col]) {
                    GameState newState = game.playMove(player, new Point(row, col));
                    TreeNode childNode = new TreeNode(newState, newState.getOpposingPlayer(player), node);
                    childNode.moveMade = new Point(row, col);
                    node.children.add(childNode);
                }
            }
        }
    }




    /**
     * Backpropagate the result of the simulation up the tree.
     */
    private void backPropagate(TreeNode node, int result) {
        while (node != null) {
            node.total++;
            node.wins += result;
            node = node.parent;
        }
    }
    private TreeNode constructTree(GameState game, Player playerIAm, Evaluator e, long startTime, int maxSearchTime) {
        // Create the root node of the tree.
        TreeNode root = new TreeNode(game, playerIAm, null);
        // Runs an initial simulation from the root node to set up the tree correctly.
        int rootResult = simulate(game, playerIAm, playerIAm, e, startTime, maxSearchTime);
        root.total += 1;
        root.wins += rootResult;
        // Starting the MCTS loop.
        while ((System.currentTimeMillis() - startTime < maxSearchTime) && (!useMaxSims || (simulationsRun < maxSims))) {
            // Select the most promising node using UCT score.
            TreeNode selectedNode = selectPromisingNode(root);
            // Expand the node by adding all possible children if it is not a leaf node.
            if (!selectedNode.state.isOver() && selectedNode.children.isEmpty()) {
                expandNode(selectedNode, selectedNode.state, selectedNode.currentPlayer);
            }
            // Simulate a game from the newly expanded node.
            int result = simulate(selectedNode.state, selectedNode.currentPlayer, playerIAm, e, startTime, maxSearchTime);
            // Backpropagate the result of the simulation up the tree.
            backPropagate(selectedNode, result);
            // Increment simulation count
            simulationsRun++;
        }
        return root;
    }



    // Inside getMaxMoveIterative method
    private Point getMaxMoveIterative(GameState game, Player player, Evaluator e, long startTime, int timeLimit) {
        int currentDepth = 1;
        Point bestMove = null;
        while (System.currentTimeMillis() - startTime < timeLimit) {
            IterativeMinimaxDecider minimaxDecider = new IterativeMinimaxDecider(currentDepth);
            Point currentBestMove = minimaxDecider.getMaxMove(game, currentDepth, startTime, timeLimit, e, player);
            if (System.currentTimeMillis() - startTime < timeLimit) {
                bestMove = currentBestMove;
            }
            currentDepth++;
        }
        return bestMove;
    }

}
