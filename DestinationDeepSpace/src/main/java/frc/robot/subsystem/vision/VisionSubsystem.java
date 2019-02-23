/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018 FIRST. All Rights Reserved.                             */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot.subsystem.vision;

import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import frc.robot.subsystem.BitBucketSubsystem;
import frc.robot.subsystem.lighting.LightingControl;
import frc.robot.subsystem.lighting.LightingSubsystem;
import frc.robot.subsystem.lighting.LightingConstants.LightingObjects;

/**
 * Add your docs here.
 */
public class VisionSubsystem extends BitBucketSubsystem {
  	// Put methods for controlling this subsBitBucketSubsystemystem
  	// here. Call these from Commands.

	// Singleton method; use VisionSubsystem.instance() to get the VisionSubsystem instance.
	public static VisionSubsystem instance() {
		if(inst == null)
			inst = new VisionSubsystem();
		return inst;		
	}
	private static VisionSubsystem inst;

	enum IlluminatorState
	{
		UNKNOWN,
		OFF,
		SNORE,
		ON
	}
	private IlluminatorState illuminatorState = IlluminatorState.UNKNOWN;
	
	private VisionSubsystem()
	{
		setName("VisionSubsystem");
	}

	private LightingSubsystem lightingSubsystem = LightingSubsystem.instance();

	private NetworkTableInstance networkTable = NetworkTableInstance.getDefault();
	private NetworkTable bvTable = networkTable.getTable("BucketVision");
	private NetworkTableEntry bvStateEntry = bvTable.getEntry("BucketVisionState");
	private NetworkTableEntry bvCameraNumber = bvTable.getEntry("CameraNum");

	@Override
	protected void initDefaultCommand() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void periodic() {
		clearDiagnosticsEnabled();		
		if (ds.isDisabled())
		{
			setIlluminatorSnore();
		}
		else if (ds.isTest())
		{
			setIlluminatorOff();			
		}
		else
		{
			enableFront();
		}

		updateBaseDashboard();	
		if (getTelemetryEnabled())
		{
			
		}
	}

	@Override
	public void diagnosticsInitialize() {
		// TODO Auto-generated method stub
	}

	@Override
	public void diagnosticsPeriodic() {
		updateBaseDashboard();
		if (getDiagnosticsEnabled())
		{

			/// TODO: Add controls for illuminator on/off and camera controls here
		}


	}

	@Override
	public void diagnosticsCheck() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void initialize() {

		initializeBaseDashboard();

		// Turn on illuminator in a snoring posture
		bvStateEntry.setString("UNKNOWN");
		setIlluminatorSnore();
	}

	public void enableFront()
	{
		bvCameraNumber.setNumber(0.0);
		setIlluminatorOn(true,VisionConstants.DEFAULT_ILLUMINATOR_BRIGHTNESS);

	}
	public void enableBack()
	{
		bvCameraNumber.setNumber(0.0);
		setIlluminatorOn(true,VisionConstants.DEFAULT_ILLUMINATOR_BRIGHTNESS);
	}

	protected boolean isIlluminatorReady()
	{
		return lightingSubsystem.isReady();
	}

	protected void setIlluminatorOff()
	{
		if (illuminatorState != IlluminatorState.OFF)
		{
			lightingSubsystem.set(LightingObjects.FRONT_CAMERA,
								LightingControl.FUNCTION_OFF,
								LightingControl.COLOR_BLACK,
								0,
								0);
			lightingSubsystem.set(LightingObjects.BACK_CAMERA,
								LightingControl.FUNCTION_OFF,
								LightingControl.COLOR_BLACK,
								0,
								0);
			illuminatorState = lightingSubsystem.isReady()?IlluminatorState.OFF:IlluminatorState.UNKNOWN;
		}
	}

	public void setIlluminatorOn(boolean front, int brightness)
	{
		if (illuminatorState != IlluminatorState.ON)
		{
			lightingSubsystem.set(front?LightingObjects.FRONT_CAMERA:LightingObjects.BACK_CAMERA,
								LightingControl.FUNCTION_ON,
								LightingControl.COLOR_GREEN,
								0,
								0,
								brightness);
			lightingSubsystem.set(front?LightingObjects.BACK_CAMERA:LightingObjects.FRONT_CAMERA,
								LightingControl.FUNCTION_OFF,
								LightingControl.COLOR_BLACK,
								0,
								0,
								0);

			illuminatorState = lightingSubsystem.isReady()?IlluminatorState.ON:IlluminatorState.UNKNOWN;
		}		
	}
	protected void setIlluminatorSnore()
	{
		if (illuminatorState != IlluminatorState.SNORE)
		{
			lightingSubsystem.set(LightingObjects.FRONT_CAMERA,
								LightingControl.FUNCTION_SNORE,
								LightingControl.COLOR_VIOLET,
								0,
								0);			
			lightingSubsystem.set(LightingObjects.BACK_CAMERA,
								LightingControl.FUNCTION_SNORE,
								LightingControl.COLOR_VIOLET,
								0,
								0);			

			illuminatorState = lightingSubsystem.isReady()?IlluminatorState.SNORE:IlluminatorState.UNKNOWN;
		}				
	}

}
