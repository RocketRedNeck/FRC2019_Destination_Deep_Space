/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot.subsystem.scoring;

import frc.robot.MotorId;
import frc.robot.operatorinterface.OI;
import frc.robot.subsystem.BitBucketSubsystem;
import frc.robot.subsystem.scoring.ScoringConstants.BeakPosition;
import frc.robot.subsystem.vision.VisionSubsystem;
import frc.robot.utils.talonutils.TalonUtils;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.LimitSwitchNormal;
import com.ctre.phoenix.motorcontrol.LimitSwitchSource;
import com.ctre.phoenix.motorcontrol.NeutralMode;






public class ScoringSubsystem extends BitBucketSubsystem {
	private final OI oi = OI.instance();

	// Singleton method; use ScoringSubsystem.instance() to get the ScoringSubsystem instance.
	public static ScoringSubsystem instance() {
		if(inst == null)
			inst = new ScoringSubsystem();
		return inst;
	}
	private static ScoringSubsystem inst;


	private BeakPosition beakPosition = BeakPosition.HATCH_RELEASE_BEAK;


	private Idle initialCommand;

	private final WPI_TalonSRX rollerMotor;
	private final WPI_TalonSRX armMotor1;
	private final WPI_TalonSRX armMotor2;
	private final WPI_TalonSRX beakMotor;
	private double armMotor1Current_amps = 0;
	private double armMotor2Current_amps = 0;

	// last orientation of the robot's arm
	// true --> front
	// false --> back
	private boolean back = false;
	// last level the arm was at
	private ScoringConstants.ScoringLevel lastLevel = ScoringConstants.ScoringLevel.NONE;
	private ScoringConstants.ScoringLevel commandedLevel = ScoringConstants.ScoringLevel.NONE;

	private double lastManualAngle = 0;


	private VisionSubsystem visionSubsystem = VisionSubsystem.instance();


	private ScoringSubsystem() {
		setName("ScoringSubsystem");



		rollerMotor = new WPI_TalonSRX(MotorId.INTAKE_MOTOR_ID);
		armMotor1   = new WPI_TalonSRX(MotorId.ARM_MOTOR1_ID);	// TODO: Rename to armMotor1 and 2
		armMotor2   = new WPI_TalonSRX(MotorId.ARM_MOTOR2_ID);
		beakMotor   = new WPI_TalonSRX(MotorId.BEAK_MOTOR_ID);

		// initialize motors before setting sensor positions and follower modes
		// otherwise, it may clear those settings
		TalonUtils.initializeMotorDefaults(rollerMotor);
		TalonUtils.initializeMotorDefaults(armMotor1);
		TalonUtils.initializeMotorDefaults(armMotor2);
		TalonUtils.initializeMotorDefaults(beakMotor);

		rollerMotor.setInverted(ScoringConstants.ROLLER_MOTOR_INVERSION);
		armMotor1.setInverted(ScoringConstants.ARM_MOTOR_INVERSION);
		armMotor2.setInverted(ScoringConstants.ARM_MOTOR_INVERSION);
		beakMotor.setInverted(ScoringConstants.BEAK_MOTOR_INVERSION);

		armMotor1.setSensorPhase(ScoringConstants.ARM_MOTOR_SENSOR_PHASE);
		beakMotor.setSensorPhase(ScoringConstants.BEAK_MOTOR_SENSOR_PHASE);

		armMotor1.configForwardLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen,0);
		armMotor1.configReverseLimitSwitchSource(LimitSwitchSource.FeedbackConnector,LimitSwitchNormal.NormallyOpen,0);
		
		armMotor1.overrideLimitSwitchesEnable(true);

		armMotor1.setNeutralMode(NeutralMode.Brake);
		beakMotor.setNeutralMode(NeutralMode.Brake);


		TalonUtils.initializeMotorFPID(armMotor1, 
							ScoringConstants.ARM_MOTION_MAGIC_KF, 
							ScoringConstants.ARM_MOTION_MAGIC_KP, 
							ScoringConstants.ARM_MOTION_MAGIC_KI, 
							ScoringConstants.ARM_MOTION_MAGIC_KD, 
							ScoringConstants.ARM_MOTION_MAGIC_IZONE);
		
		TalonUtils.initializeMotorFPID(beakMotor, 
							ScoringConstants.BEAK_MOTION_MAGIC_KF, 
							ScoringConstants.BEAK_MOTION_MAGIC_KP, 
							ScoringConstants.BEAK_MOTION_MAGIC_KI, 
							ScoringConstants.BEAK_MOTION_MAGIC_KD, 
							ScoringConstants.BEAK_MOTION_MAGIC_IZONE);

		// TODO: Configure armMotor1 to use initializeMagEncoderRelativeMotor
		TalonUtils.initializeMagEncoderRelativeMotor(armMotor1, 1);
		TalonUtils.initializeMagEncoderRelativeMotor(beakMotor, 1);


		armMotor2.follow(armMotor1);

		
		
		// Acceleration is the slope of the velocity profile used for motion magic
		// Example: 250 tick/100ms/s is 2500 ticks/s/s
		armMotor1.configMotionAcceleration(ScoringConstants.ARM_ACCELERATION_TICKS_PER_100MS_PER_SEC, 20);
		armMotor1.configMotionCruiseVelocity(ScoringConstants.ARM_CRUISE_SPEED_TICKS_PER_100MS, 20);

		beakMotor.configMotionAcceleration(ScoringConstants.BEAK_ACCELERATION_TICKS_PER_100MS_PER_SEC, 20);
		beakMotor.configMotionCruiseVelocity(ScoringConstants.BEAK_CRUISE_SPEED_TICKS_PER_100MS, 20);
		


		setAllMotorsZero();




		// TODO:
		// NOTE: It may need to be biased based on where the shaft was set when assembled
		int abs_ticks = armMotor1.getSensorCollection().getPulseWidthPosition() & 0xFFF;
		// set the ticks of relative magnetic encoder
		// effectively telling the encoder where 0 is
		armMotor1.setSelectedSensorPosition(abs_ticks - ScoringConstants.ARM_BIAS_TICKS);
		beakMotor.setSelectedSensorPosition(0); // The beak is normally at rest during initialization, this makes sure the motor knows that.



		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {}
		double angle = getAngle_deg();
		if (angle <= -180) {
			angle += 360;
		}
		else if (angle >= 180) {
			angle -= 360;
		}
		setAngle_deg(angle);
	}





	/*
	 * I drew this for a method I realized we didn't even need but decided to keep it, enjoy!
	 * 
	 * 
	 *        \   /            \   /                               \   /            \   /
	 *         \_/              \_/               G O               \_/              \_/
 	 *          \\               \\                                 //               //
	 *           \\               \\              BIT              //               //
	 *       _____\\_____     _____\\_____                   _____//_____     _____//_____
	 *       |          |     |          |      BUCKETS      |          |     |          |
	 *      ==O========O==   ==O========O==                 ==O========O==   ==O========O==
	 */


	
	// Put methods for controlling this subsystem
	// here. Call these from Commands.


	public ScoringConstants.ScoringLevel getCommandedLevel()
	{
		return commandedLevel;
	}

	/** Command the arm to a level */
	public void goToLevel(ScoringConstants.ScoringLevel level) {
		// neither level should get to here in the first place
		//     ... but just in case
		commandedLevel = level;
		if (
			level == ScoringConstants.ScoringLevel.NONE ||
			level == ScoringConstants.ScoringLevel.INVALID
		) {
			return;
		} else if (level == ScoringConstants.ScoringLevel.MANUAL){
			lastLevel=ScoringConstants.ScoringLevel.MANUAL;
			lastManualAngle = getAngle_deg();
			return;
		}


		double angle_rad = level.getAngle_rad();

		// .switchOrientation() needs to know the last level the arm was at
		// if not, then whether the arm should be in the front or back is useless
		// because we have no way to recommand it to the right level
		lastLevel = level;

		directArmTo(angle_rad);
	}



	/**
	 * + pow --> spit out
	 * - pow --> intake
	 */
	public void setRollers(double pow) {
		// may be the other way around depending on the placement of the motors and such
		// currently this assumes that a + signal to the top roller will cause it to intake

		/*
		 *    <---  __
		 *         /  \
		 *         \__/  --->
		 * 
		 *         O (ball) (NOT TO SCALE) -->
		 * 
		 *          __   --->
		 *         /  \
		 *    <--- \__/
		 */
		rollerMotor.set(ControlMode.PercentOutput, pow);
	}



	/* stop all current subsystem functions (used in Idle) */
	public void disable() {
		setAllMotorsZero();
	}



	/* switch the orientation of the arm */
	public void switchOrientation() {
		back = !back;

		if (back)
		{
			visionSubsystem.enableBack();
		}
		else
		{
			visionSubsystem.enableFront();
		}

		// go to the last level the arm was at, but this time
		// with the new orientation (handled by the method)
		goToLevel(lastLevel);
	}





	/** Get the selected level on the joystick */
	// Used so much in commands that I just put it in the subsystem
	public ScoringConstants.ScoringLevel getSelectedLevel() {
		boolean hp = oi.hp();
        boolean ground = oi.ground();
        boolean bCargo = oi.bCargo();
        boolean bLoadingStation = oi.bLoadingStation();
		boolean bRocket1 = oi.bRocket1();
		boolean topDeadCenter = oi.topDeadCenter();
		double manual = oi.manualArmControl();
		
		ScoringConstants.ScoringLevel level = ScoringConstants.ScoringLevel.NONE;

		if (manual > ScoringConstants.ARM_MANUAL_DEADBAND || manual < -ScoringConstants.ARM_MANUAL_DEADBAND) {
			level = ScoringConstants.ScoringLevel.MANUAL;
		}
		if (hp) {
			if (level == ScoringConstants.ScoringLevel.NONE)
			{
				level = ScoringConstants.ScoringLevel.HP;
			}
			else
			{
				level = ScoringConstants.ScoringLevel.INVALID;
			}
		}
		if (topDeadCenter)
		{
			if (level == ScoringConstants.ScoringLevel.NONE)
			{ 
				level = ScoringConstants.ScoringLevel.TOP_DEAD_CENTER; 
			}
			else 
			{ 
				return ScoringConstants.ScoringLevel.INVALID; 
			}
		}
		if (ground) {
			if (level == ScoringConstants.ScoringLevel.NONE)
			{ 
				level = ScoringConstants.ScoringLevel.GROUND; 
			}
			else 
			{ 
				return ScoringConstants.ScoringLevel.INVALID; 
			}
		}
		if (bCargo) {
			if (level == ScoringConstants.ScoringLevel.NONE) { level = ScoringConstants.ScoringLevel.BALL_CARGO; }
			else { return ScoringConstants.ScoringLevel.INVALID; }
		}
		if (bLoadingStation) {
			if (level == ScoringConstants.ScoringLevel.NONE) { level = ScoringConstants.ScoringLevel.BALL_LOADING_STATION; }
			else { return ScoringConstants.ScoringLevel.INVALID; }
		}
		if (bRocket1) {
			if (level == ScoringConstants.ScoringLevel.NONE) { level = ScoringConstants.ScoringLevel.BALL_ROCKET_1; }
			else { return ScoringConstants.ScoringLevel.INVALID; }
		}

		return level;
	}



	public int getArmLevelTickError() {
		int err1 = Math.abs(armMotor1.getClosedLoopError());

		// Always output this
		//SmartDashboard.putNumber(getName() + "/ArmLevelError (ticks)", err1);

		return err1;
	}





	// Internal control of subsystem



	private void setAllMotorsZero() {
		rollerMotor.set(ControlMode.PercentOutput, 0);
		armMotor1.set(ControlMode.PercentOutput, 0);
		beakMotor.set(ControlMode.PercentOutput, 0);
	}



	/**
	 * Direct the robot arm to a certain angle.
	 */
	// private because we only want other classes to change the angle via goToLevel()
	private double targetAngle_rad = 0.0;
	public double getTargetAngle_rad()
	{
		return targetAngle_rad;
	}

	public void directArmTo(double angle_rad) {
		targetAngle_rad = angle_rad;
		double ticks = angle_rad * ScoringConstants.ARM_MOTOR_NATIVE_TICKS_PER_REV / (2 * Math.PI);

		// if the arm is in the back of the robot
		if (back == false) {
			// switch the ticks so that the arm will go to intended position on the back too
			// ticks = 0 means arm is just up
			ticks *= -1;
			targetAngle_rad *= -1;
			//SmartDashboard.putNumber(getName()+"/Arm Command Angle (deg)", Math.toDegrees(-angle_rad));

		}
		else
		{
			//SmartDashboard.putNumber(getName()+"/Arm Command Angle (deg)", Math.toDegrees(angle_rad));
		}

		armMotor1.set(ControlMode.MotionMagic, ticks);
	}



	/** Get angle from normal of scoring arm (-90 deg = exactly forward, +90 is backward) */
	public double getAngle_deg() {
		int ticks = armMotor1.getSelectedSensorPosition();

		return 360.0 * (double)ticks / (double)ScoringConstants.ARM_MOTOR_NATIVE_TICKS_PER_REV;
	}



	public void setAngle_deg(double deg) {
		int ticks = (int) (ScoringConstants.ARM_MOTOR_NATIVE_TICKS_PER_REV * deg / 360);

		armMotor1.setSelectedSensorPosition(ticks);
	}
	



	
	public void startIdle() {
		// Don't use default commands as they can catch you by surprise
		System.out.println("Starting " + getName() + " Idle...");
		if (initialCommand == null) {
			initialCommand = new Idle(); // Only create it once
		}
		initialCommand.start();
	}





	@Override
	protected void initDefaultCommand() {
	}

	private static int currentLimitCount = 0;
	public boolean exceededCurrentLimit()
	{
		armMotor1Current_amps = armMotor1.getOutputCurrent();
		armMotor2Current_amps = armMotor2.getOutputCurrent();

		boolean currentLimit = (armMotor1Current_amps >= ScoringConstants.MAX_ARM_MOTOR_CURRENT_AMPS) ||
							   (armMotor2Current_amps >= ScoringConstants.MAX_ARM_MOTOR_CURRENT_AMPS);
		if (currentLimit)
		{
			currentLimitCount++;
		}
		//SmartDashboard.putNumber(getName() + "/Arm Current Limit Count", currentLimitCount);
		return currentLimit;
	}

	public boolean frontLimit()
	{
		boolean result = armMotor1.getSensorCollection().isRevLimitSwitchClosed();
		SmartDashboard.putBoolean(getName()+"/Arm Front Limit", result);

		if (result) {
			setAngle_deg(ScoringConstants.FRONT_LIMIT_ANGLE);
		}

		return result;
	}
	public boolean backLimit()
	{
		boolean result = armMotor1.getSensorCollection().isFwdLimitSwitchClosed();
		SmartDashboard.putBoolean(getName()+"/Arm Back Limit", result);

		if (result) {
			setAngle_deg(ScoringConstants.BACK_LIMIT_ANGLE);
		}
		
		return result;
	}

	@Override
	public void periodic() {
		
		if (exceededCurrentLimit())
		{
			startIdle();
		}

		// Just poll it
		frontLimit();
		backLimit();

		if (oi.autoAlign() && lastLevel == ScoringConstants.ScoringLevel.HP) {
			goToLevel(ScoringConstants.ScoringLevel.HP_AUTO);
		}

		if (!oi.autoAlign() && lastLevel == ScoringConstants.ScoringLevel.HP_AUTO) {
			goToLevel(ScoringConstants.ScoringLevel.HP);
		}

		if (Math.abs(oi.manualArmControl())>ScoringConstants.ARM_MANUAL_DEADBAND && lastLevel == ScoringConstants.ScoringLevel.MANUAL) {

			// Sets the speed of the arm to a value between -10 and 10.
			double manualArmSpeed = oi.manualArmControl() * ScoringConstants.ARM_MANUAL_SPEED_MAX;

			

			if (lastManualAngle < 0){
				manualArmSpeed = manualArmSpeed * -1;
			}

			// Sets targetAngle to the current arm angle plus the arm speed.
			// Multiplying by the time period causes targetAngle to increment by manualArmSpeed degrees per second.
			double targetAngle = lastManualAngle + (manualArmSpeed * period);
			lastManualAngle = targetAngle;
			targetAngle = Math.abs(targetAngle);

			//SmartDashboard.putNumber(getName()+"/Arm Manual Target Angle (deg)", targetAngle);

			// Tells the arm to move to targetAngle.
			directArmTo(Math.toRadians(targetAngle));

		}

		// note: the OI is set up so that infeed and outfeed WILL BE MUTUALLY EXCLUSIVE
		// and the operator has secondary priority for infeed and outfeed
		boolean infeed = oi.infeedActive();
		boolean outfeed = oi.outfeedActive();
		boolean hatchOutfeed = (getCommandedLevel() == ScoringConstants.ScoringLevel.HP);
		//SmartDashboard.putBoolean(getName()+"/Infeed", infeed);
		//SmartDashboard.putBoolean(getName()+"/Outfeed", outfeed);

		if      (infeed)  { setRollers(-1.0); }
		else if (outfeed) { setRollers(1.0);  }
		else              { setRollers(0.0);  }

		if (back)
		{
			visionSubsystem.enableBack();
		}
		else
		{
			visionSubsystem.enableFront();
		}

		boolean grapple = oi.beakGrapple();
		boolean release = oi.beakRelease();
		if (grapple)
		{
			beakPosition = BeakPosition.HATCH_GRAPPLE_BEAK;
		}
		else if (release)
		{
			beakPosition = BeakPosition.HATCH_RELEASE_BEAK;
		}

		switch(beakPosition)
		{
			case HATCH_GRAPPLE_BEAK:
				/// TODO: THis pulses between current limit and commanded position
				/// fix in a bit
				if(beakMotor.getOutputCurrent() >= ScoringConstants.BEAK_MAX_CURRENT_AMPS) {
					beakMotor.set(ControlMode.MotionMagic, beakMotor.getSelectedSensorPosition()); // Tell the beak motor to holod it's current position if the current output exceeds BEAK_MAX_CURRENT_AMPS.
				} else {
					beakMotor.set(ControlMode.MotionMagic, beakPosition.getBeak_ticks()); // Tell the beak to go to the position set in the enum.
				}
				break;

			case HATCH_RELEASE_BEAK:
				beakMotor.set(ControlMode.MotionMagic, beakPosition.getBeak_ticks()); // Tell the beak to go to the position set in the enum.
				break;

			default:
				break;
		}
		
		clearDiagnosticsEnabled();
		updateBaseDashboard();
		SmartDashboard.putBoolean(getName()+ "/Arm FRONT", !back);
		SmartDashboard.putNumber(getName() + "/Arm Angle", getAngle_deg());
		if (getTelemetryEnabled()) {
			SmartDashboard.putNumber(getName() + "/Arm Ticks", armMotor1.getSelectedSensorPosition());
			SmartDashboard.putNumber(getName() + "/Arm Error", armMotor1.getClosedLoopError());
			SmartDashboard.putNumber(getName() + "/Arm Motor 0 Current", armMotor1Current_amps);
			SmartDashboard.putNumber(getName() + "/Arm Motor 1 Current", armMotor2Current_amps);
			SmartDashboard.putNumber(getName() + "/Arm Motor TOTAL Current", armMotor1Current_amps+armMotor2Current_amps);
			SmartDashboard.putNumber(getName() + "/Beak Ticks", beakMotor.getSelectedSensorPosition());     // Log the beak motor rotation in ticks.
			SmartDashboard.putNumber(getName() + "/Beak Error", beakMotor.getClosedLoopError());            // Log the beak motor error in ticks.
			SmartDashboard.putNumber(getName() + "/Beak Motor Current", beakMotor.getOutputCurrent());      // Log the beak output current in amps.

		}
		// commands will handle dealing with arm manipulation
	}

	@Override
	public void diagnosticsInitialize() {
	}

	@Override
	public void diagnosticsPeriodic() {
		updateBaseDashboard();
		if (getDiagnosticsEnabled())
		{
			double angle = SmartDashboard.getNumber(getName() + "/Test Angle", 0);
			directArmTo(angle);
		}

		// commands will handle dealing with arm manipulation
	}

	@Override
	public void diagnosticsCheck() {		
	}

	@Override
	public void initialize() {
		initializeBaseDashboard();

		//SmartDashboard.putNumber(getName() + "/Test Angle", 0);
	}










	// Physics sim commands



	public TalonSRX getArmMotor1() {
		return armMotor1;
	}
	
	public void manualArmOperate() {
		armMotor1.set(ControlMode.PercentOutput, oi.manualArmRotate());
	}
	public TalonSRX getRollerMotor() {
		return rollerMotor;
	}
}