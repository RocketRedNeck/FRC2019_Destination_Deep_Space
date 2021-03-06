package frc.robot.utils.autotuner.steps;

import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

/**
 * identification of initial cruise speed (85% of max)
 *     Cs = 0.85 * tp100
 * invocation of motion magic mode and command some rotations, R (e.g., 10)
 *     Make note of the error in ticks (terr)
 */
public class CruiseStep extends TuningStep {
    private int terr;


    
    public CruiseStep(int kf_tp100) {
        super(DataCollectionType.Position);

        value = (int) (0.75 * kf_tp100);
        valueString = value + "";

        log("cruise velocity: " + value + " ticks per 100ms\n");
        log("Getting error with kF and cruise velocity...");
    }

    

    public boolean update() {
        // get + and - positions
        boolean done = collectData();

        // if done with that, get average speed at %
        if (done) {
            String rep = "";

            rep += "average positive error: " + error_pos.average() + " ticks \n";
            rep += "average negative error: " + error_neg.average() + " ticks \n\n";

            terr = (int) ((error_pos.average() + error_neg.average()) / 2); // average of two (average) errors

            rep += "average total error: " + terr + " ticks";

            log(rep);
        }

        return done;
    }

    public int getTickError() {
        return terr;
    }
}