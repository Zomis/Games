package net.zomis.games.ur.ais;

import net.zomis.fight.ext.FightCollectors;
import net.zomis.fight.ext.WinResult;
import net.zomis.fight.ext.WinStats;
import net.zomis.games.ur.RoyalGameOfUr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.ToIntFunction;
import java.util.stream.Collector;
import java.util.stream.IntStream;

public class MonteCarloAI implements ToIntFunction<RoyalGameOfUr> {

    private static final Logger logger = LoggerFactory.getLogger(MonteCarloAI.class);

    private final int fights;
    private final ToIntFunction<RoyalGameOfUr> ai;

    public MonteCarloAI(int fights, ToIntFunction<RoyalGameOfUr> ai) {
        this.fights = fights;
        this.ai = ai;
    }

    public int positionToMove(RoyalGameOfUr game) {
        int[] possibleActions = getPossibleActions(game);
        if (possibleActions.length == 1) {
            return possibleActions[0];
        }

        double best = 0;
        int bestAction = -1;
        int me = game.getCurrentPlayer();
        for (int action : possibleActions) {
            RoyalGameOfUr copy = game.copy();
            copy.move(game.getCurrentPlayer(), action, game.getRoll());
            double expectedWin = fight(copy, me);
            logger.info("Action {} in state {} has {}", action, game, expectedWin);
            if (expectedWin > best) {
                bestAction = action;
                best = expectedWin;
            }
        }
        int aiResult = ai.applyAsInt(game);
        if (aiResult != bestAction) {
            logger.warn("Monte Carlo returned different result than its simulation AI in state {}." +
                " AI {} - Monte Carlo {}", game, aiResult, bestAction);
        }
        return bestAction;
    }

    private double fight(RoyalGameOfUr game, int me) {
        Collector<WinResult, ?, WinStats> collector = FightCollectors.stats();
        return IntStream.range(0, this.fights)
            .parallel()
            .mapToObj(i -> singleFight(game.copy(), me))
            .collect(collector)
            .getPercentage();
    }

    private WinResult singleFight(RoyalGameOfUr game, int me) {
        while (!game.isFinished()) {
            while (game.isRollTime()) {
                game.doRoll();
            }

            int movePosition = ai.applyAsInt(game);
            boolean allowed = game.move(game.getCurrentPlayer(), movePosition, game.getRoll());
            if (!allowed) {
                // IntelliJ complains about "Cannot resolve constructor" - I disagree, it works just fine
                throw new java.lang.IllegalStateException("Unexpected move: " + game.toCompactString() + ": " + movePosition);
            }
        }
        return WinResult.resultFor(game.getWinner(), me, -1);
    }

    final int[] getPossibleActions(RoyalGameOfUr game) {
        return IntStream.range(0, 15).filter(i -> game.canMove(game.getCurrentPlayer(), i, game.getRoll())).toArray();
    }

    @Override
    public int applyAsInt(RoyalGameOfUr game) {
        return this.positionToMove(game);
    }
}
