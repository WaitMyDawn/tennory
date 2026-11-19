package yagen.waitmydawn.tennory.glaive;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.tennory.combat.GlaiveRules;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlaiveRulesTest {
    @Test
    void clipsFlightAgainstThePlayersCurrentPosition() {
        double requested = GlaiveRules.THROW_SPEED;
        assertEquals(0.5, GlaiveRules.outboundDistance(63.5 * 63.5, 63.5, requested), 1e-9);
        assertEquals(requested, GlaiveRules.outboundDistance(63.5 * 63.5, -63.5, requested), 1e-9);
        assertEquals(0.0, GlaiveRules.outboundDistance(65.0 * 65.0, -65.0, requested), 1e-9);
    }

    @Test
    void clipsAnObliqueFlightSegmentEvenWhenNeitherAxisReachesTheLimit() {
        double expected = Math.sqrt(64.0 * 64.0 - 63.995 * 63.995);
        assertEquals(expected, GlaiveRules.outboundDistance(63.995 * 63.995, 0.0, GlaiveRules.THROW_SPEED), 1e-9);
    }

    @Test
    void usesOneBlockPerTickOutboundAndOneAndAQuarterReturning() {
        assertEquals(1.0, GlaiveRules.THROW_SPEED, 1e-9);
        assertEquals(1.25, GlaiveRules.RETURN_SPEED, 1e-9);
    }

    @Test
    void reducesTheSidewaysSteeringAsTheGlaiveApproachesTheCatchPoint() {
        assertEquals(1.0, GlaiveRules.detonationReturnCurve(64.0), 1e-9);
        assertEquals(0.5, GlaiveRules.detonationReturnCurve(16.0), 1e-9);
        assertEquals(0.0, GlaiveRules.detonationReturnCurve(0.0), 1e-9);
    }
}
