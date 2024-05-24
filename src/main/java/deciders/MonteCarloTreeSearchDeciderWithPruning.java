package deciders;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;

import evaluators.Evaluator;
import games.GameState;
import players.Player;

/**
 * This class implements an enhanced Monte Carlo Tree Search (MCTS) algorithm with pruning and various optimizations.
 */
public class MonteCarloTreeSearchDeciderWithPruning extends FixedMinimaxDecider {

    // Fields for storing the search parameters.
    private boolean hardPlayouts;
    private boolean useMaxSims;
    private int maxSims;
    private double randomChance;
    private int timePerMinimax;

    // Statistical field(s).
    private int simulationsRun = 0;

    public MonteCarloTreeSearchDeciderWithPruning(boolean useMaxSims, int maxSims, boolean useMinimax, int minimaxDepth, double randomChance, int timePerMinimax) {
        super(minimaxDepth);
        this.useMaxSims = useMaxSims;
        this.maxSims = maxSims;
        this.hardPlayouts = useMinimax;
        this.randomChance = randomChance;
        this.timePerMinimax = timePerMinimax;
    }

    public String getType() {
        return "MCTSWithPruning";
    }

    public String toFileString() {
        return "MCTSWithPruning(" + this.useMaxSims + "," + this.maxSims + "," + this.hardPlayouts + "," + this.depthToSearchTo + "," + this.randomChance + "," + this.timePerMinimax + ")";
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
                    }
                    // Otherwise use the move specified by the internal decider to simulate moves.
                    else {
                        moveToPlay = getMaxMove(currentState, depthToSearchTo, System.currentTimeMillis(), timePerMinimax, e, currentPlayer);
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

        public TreeNode() {
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
    private TreeNode constructTree(GameState game, Player playerIAm, Evaluator e, long startTime, int maxSearchTime) {

        // Create the root node of the tree.
        TreeNode root = new TreeNode();

        // Runs an initial simulation from the root node to set up the tree correctly.
        int rootResult = simulate(game, playerIAm, playerIAm, e, startTime, maxSearchTime);
        root.total += 1;
        root.wins += rootResult;

        // Starting the MCTS loop.
        while ((System.currentTimeMillis() - startTime < maxSearchTime) && (!useMaxSims || simulationsRun < maxSims)) {

            // Begins search at the root node, with an empty path list.
            TreeNode currentNode = root;
            GameState currentGame = new GameState(game);
            Player currentPlayer = playerIAm;

            ArrayList<TreeNode> path = new ArrayList<>();
            path.add(currentNode);

            // Moves through the tree until a leaf node is found. (SELECTION)
            while (currentNode.children.size() > 0) {

                // Stores the best move found so far.
                TreeNode bestMove = null;
                double bestUCB1 = Double.NEGATIVE_INFINITY;

                // Checks each child node to determine the next move to be made.
                for (TreeNode childNode : currentNode.children) {
                    double childUCB1 = getUCTScore(childNode.wins, childNode.total, currentNode.total);
                    if (childUCB1 > bestUCB1) {
                        bestUCB1 = childUCB1;
                        bestMove = childNode;
                    }
                }

                // Add next move to the path, and update current variables.
                path.add(bestMove);
                currentNode = bestMove;
                if (currentNode.moveMade.x != -1 || currentNode.moveMade.y != -1) {
                    // A Point with coordinates (-1,-1) is used to show a move where the player cannot play.
                    // The if statement here ensures that this lack of change in the game state is correctly handled.
                    currentGame = currentGame.playMove(currentPlayer, currentNode.moveMade);
                }
                currentPlayer = currentGame.getOpposingPlayer(currentPlayer);
            }

            // Adds children to the current node if it has none.
            if (currentNode.children.size() == 0) {

                // Code for creating nodes for each legal move.
                if (currentGame.hasLegalMoves(currentPlayer) && (!currentGame.isOver())) {

                    // Gets the legal move array, and loops through all legal moves.
                    boolean[][] legalMoves = currentGame.getLegalMoves(currentPlayer);
                    int totalMoves = 0;
                    int totalWins = 0;
                    List<TreeNode> nodesToPrune = new ArrayList<>(); // Store the nodes to be pruned
                    for (int row = 0; row < currentGame.getBoardDims()[0]; ++row) {
                        for (int col = 0; col < currentGame.getBoardDims()[1]; ++col) {
                            if (legalMoves[row][col]) {

                                // Creating new child nodes of the tree (EXPANSION)
                                TreeNode newChildNode = new TreeNode();
                                newChildNode.moveMade = new Point(row, col);
                                currentNode.children.add(newChildNode);

                                // Children must have one simulation complete for UCB1 calculations to work correctly. (SIMULATION)
                                GameState childState = currentGame.playMove(currentPlayer, new Point(row, col));
                                int childResult = simulate(childState, childState.getOpposingPlayer(currentPlayer), playerIAm, e, startTime, maxSearchTime);
                                newChildNode.total += 1;
                                newChildNode.wins += childResult;
                                totalMoves += 1;
                                totalWins += childResult;

                                // Add to nodes to be pruned if necessary
                                if (prune(newChildNode, e, childState, playerIAm)) {
                                    nodesToPrune.add(newChildNode);
                                }
                            }
                        }
                    }

                    // Remove pruned nodes from currentNode.children
                    currentNode.children.removeAll(nodesToPrune);

                    // Updating all of the nodes in the path with the simulated info (BACKPROPAGATION)
                    for (int nodeNum = path.size() - 1; nodeNum >= 0; --nodeNum) {
                        TreeNode theNode = path.get(nodeNum);
                        theNode.total += totalMoves;
                        theNode.wins += totalWins;
                    }

                } else if (currentGame.isOver()) {
                    // Code for when the game is over.
                    // Get the result of the game and update the tree accordingly. (SIMULATION + BACKPROPAGATION)
                    int gameResult = simulate(currentGame, currentPlayer, playerIAm, e, startTime, maxSearchTime);
                    for (int nodeNum = path.size() - 1; nodeNum >= 0; --nodeNum) {
                        TreeNode theNode = path.get(nodeNum);
                        theNode.total += 1;
                        theNode.wins += gameResult;
                    }
                } else {
                    // Code for when no legal moves exist.
                    // Create a tree node representing no move made. (EXPANSION)
                    TreeNode newChildNode = new TreeNode();
                    newChildNode.moveMade = new Point(-1, -1);
                    currentNode.children.add(newChildNode);

                    // Run a simulation from this node onward. (SIMULATION)
                    GameState childState = new GameState(currentGame);
                    int childResult = simulate(childState, childState.getOpposingPlayer(currentPlayer), playerIAm, e, startTime, maxSearchTime);
                    newChildNode.total += 1;
                    newChildNode.wins += childResult;

                    // Updating the counts of the nodes on the path to this node. (BACKPROPAGATION)
                    for (int nodeNum = path.size() - 1; nodeNum >= 0; --nodeNum) {
                        TreeNode theNode = path.get(nodeNum);
                        theNode.total += 1;
                        theNode.wins += childResult;
                    }
                }
            }
        }

        return root;
    }

}
