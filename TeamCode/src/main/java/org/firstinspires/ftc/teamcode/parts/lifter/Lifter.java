package org.firstinspires.ftc.teamcode.parts.lifter;

import com.qualcomm.hardware.rev.RevBlinkinLedDriver;

import org.firstinspires.ftc.teamcode.parts.drive.Drive;
import org.firstinspires.ftc.teamcode.parts.drive.DriveControl;
import org.firstinspires.ftc.teamcode.parts.lifter.hardware.LifterHardware;
import org.firstinspires.ftc.teamcode.parts.lifter.settings.LifterSettings;

import om.self.ezftc.core.Robot;
import om.self.ezftc.core.part.ControllablePart;
import om.self.task.core.TaskEx;
import om.self.task.event.EventContainer;
import om.self.task.other.TimedTask;

public class Lifter extends ControllablePart<Robot, LifterSettings, LifterHardware, LifterControl>{
    //private boolean closed = false;
    public final TimedTask autoDockTask = new TimedTask(TaskNames.autoDock, getTaskManager());
    private final TimedTask autoPreDropTask = new TimedTask(TaskNames.preAutoDrop, getTaskManager());
    private final TimedTask autoDropTask = new TimedTask(TaskNames.autoDrop, getTaskManager());

    private Drive drive;
    private final int[] coneToPos = {40,160,270,390,500}; //TODO move to settings
    private final TimedTask autoGrabTask = new TimedTask(TaskNames.autoGrab, getTaskManager());
    private final int[] poleToPos = {0,850,1650,2560}; //TODO move to settings
    public int cone; //the current cone of the stack(useful for autonomous)
    public int pole; //the pole height(0 - terminal, 1 - low, 2 - mid, 3 - high)
    private int liftTargetPosition;

    //***** Constructors *****
    public Lifter(Robot parent) {
        super(parent, "lifter", () -> new LifterControl(0,0,true));
        setConfig(
                LifterSettings.makeDefault(),
                LifterHardware.makeDefault(parent.opMode.hardwareMap)
        );
    }

    public Lifter(Robot parent, LifterSettings settings, LifterHardware hardware){
        super(parent, "lifter", () -> new LifterControl(0,0,true));
        setConfig(settings, hardware);
    }

    private void constructAutoPreDrop(){
        autoPreDropTask.autoStart = false;

        autoPreDropTask.addStep(() -> triggerEvent(ControllablePart.Events.stopControllers));
        autoPreDropTask.addStep(()->setLiftPosition(poleToPos[pole]));
       // height should be 2060 for high, (-200 for clearance)
        //changed turn pos from .286 to .141 because couldn't open all the way without hititng
        autoPreDropTask.addStep(()->setTurnPosition(.444));
        autoPreDropTask.addStep(this::isLiftInTolerance);
        autoPreDropTask.addDelay(500);
        autoPreDropTask.addStep(() -> triggerEvent(ControllablePart.Events.startControllers));
        autoPreDropTask.addStep(() -> triggerEvent(Events.preDropComplete));
    }

    public void addAutoPreDropToTask(TaskEx task){
        task.addStep(autoPreDropTask::restart);
        task.waitForEvent(eventManager.getContainer(Events.preDropComplete));
    }

    private final TimedTask coneRangeingTask = new TimedTask(TaskNames.coneMeasureRanges, getTaskManager());

    private int ultraRangeModule = 0; // keeps track of measuring ranges

    private double leftDist;
    private double rightDist;
    private double midDist;

    public void addAutoPreDropToTask(TaskEx task, int pole){
        task.addStep(() -> this.pole = pole);
        addAutoPreDropToTask(task);
    }

    public void liftWithPower(double power){
        if(Math.abs(power) < getSettings().minRegisterVal) return;

        if(power < 0)
            power *= getSettings().maxDownLiftSpeed;
        else
            power *= getSettings().maxUpLiftSpeed;

        setLiftPosition(getHardware().leftLiftMotor.getCurrentPosition() + (int)power);
    }

    public void setLiftPosition(int position){
        position = Math.min(getSettings().maxLiftPosition, Math.max(getSettings().minLiftPosition, position));
        int diff = getHardware().rightLiftMotor.getCurrentPosition() - getHardware().leftLiftMotor.getCurrentPosition();

        liftTargetPosition = position;

        getHardware().leftLiftMotor.setTargetPosition(position);
        getHardware().rightLiftMotor.setTargetPosition(position + diff);
    }

    public boolean isLiftInTolerance(){
        return Math.abs(liftTargetPosition - getLiftPosition()) <= getSettings().tolerance;
    }

    public void setLiftToTop(){
        setLiftPosition(getSettings().maxLiftPosition);
    }

    public void setLiftToBottom(){
        setLiftPosition(getSettings().minLiftPosition);
    }

    public int getLiftPosition(){
        return getHardware().leftLiftMotor.getCurrentPosition();
    }

    public double getRightUltra(){return rightDist;}
    public double getLeftUltra(){return leftDist;}
    public double getMidUltra(){return midDist;}

    public void turnWithPower(double power){
        setTurnPosition(getCurrentTurnPosition() + power);
    }

    public void setTurnPosition(double position){
        position = Math.min(getSettings().turnServoMaxPosition, Math.max(getSettings().turnServoMinPosition, position));

        getHardware().leftTurnServo.setPosition(position);
        getHardware().rightTurnServo.setPosition(position + getSettings().rightTurnServoOffset);
    }

    public double getCurrentTurnPosition(){
        return getHardware().leftTurnServo.getPosition();
    }
/*
    public void setGrabberPower(double power){
        getHardware().leftGrabServo.setPower(power);
        getHardware().rightGrabServo.setPower(power);
    }
*/
    public void setGrabberClosed(){
        getHardware().grabServo.setPosition(getSettings().grabberServoClosePos);
    }

    public void setGrabberOpen(boolean wideOpen){
        getHardware().grabServo.setPosition(wideOpen ? getSettings().grabberServoWideOpenPos : getSettings().grabberServoOpenPos);
    }

    /**
     * MUST RUN autoDropPre before
     */
    private void constructAutoDrop(){
        autoDropTask.autoStart = false;

        autoDropTask.addStep(() -> triggerEvent(ControllablePart.Events.stopControllers));
        autoDropTask.addStep(()->setTurnPosition(.141));
        autoDropTask.addStep(()->setLiftPosition(poleToPos[pole] - 200));
        autoDropTask.addStep(this::isLiftInTolerance);
        autoDropTask.addDelay(500);
        autoDropTask.addStep(()->setGrabberOpen(false));
        autoDropTask.addStep(() -> triggerEvent(ControllablePart.Events.startControllers));
        autoDropTask.addStep(() -> triggerEvent(Events.dropComplete));
    }

    public void addAutoDropToTask(TaskEx task){
        task.addStep(autoDropTask::restart);
        task.waitForEvent(eventManager.getContainer(Events.dropComplete));
    }

    private void constructAutoDock(){
        autoDockTask.autoStart = false;

        autoDockTask.addStep(() -> triggerEvent(ControllablePart.Events.stopControllers));
        autoDockTask.addStep(this::setGrabberClosed);
        autoDockTask.addStep(()->setTurnPosition(.95));
        autoDockTask.addDelay(1000);
        autoDockTask.addStep(()->setGrabberOpen(false));
        autoDockTask.addStep(()->setLiftPosition(coneToPos[cone]));
        autoDockTask.addDelay(500);
        autoDockTask.addStep(this::isLiftInTolerance);
        autoDockTask.addStep(()-> {if (cone == 0) setGrabberOpen(true);});
        autoDockTask.addStep(() -> triggerEvent(ControllablePart.Events.startControllers));
        autoDockTask.addStep(() -> triggerEvent(Events.dockComplete));
    }

    public void addAutoDockToTask(TaskEx task){
        task.addStep(autoDockTask::restart);
        task.waitForEvent(eventManager.getContainer(Events.dockComplete));
    }

    public void addAutoDockToTask(TaskEx task, int cone){
        task.addStep(() -> this.cone = cone);
        addAutoDockToTask(task);
    }
    /**
     * MUST RUN autoDock before
     */
    private void constructAutoGrab(){
        autoGrabTask.autoStart = false;

        //autoGrabTask.addDelay(2000);
        autoGrabTask.addStep(() -> setGrabberClosed());
        autoGrabTask.addDelay(500); // needed to let grabber open
        autoGrabTask.addStep(() -> setLiftPosition(coneToPos[cone] + 400));
        autoGrabTask.addStep(this::isLiftInTolerance);
        autoGrabTask.addStep(() -> triggerEvent(ControllablePart.Events.startControllers));
        autoGrabTask.addStep(() -> triggerEvent(Events.grabComplete));
    }

    public void addAutoGrabToTask(TaskEx task){
        task.addStep(autoGrabTask::restart);
        task.waitForEvent(eventManager.getContainer(Events.grabComplete));
    }

    @Override
    public void onInit() {
        //powerEdgeDetector.setOnFall(() -> se);
        constructAutoGrab();
        constructAutoDock();
        constructAutoDrop();
        constructAutoPreDrop();
        constructConeRanging();

        //add events
        eventManager.attachToEvent(Events.grabComplete, "decrement cone", () -> {if(cone > 0) cone--;});
    }

    public static final class ContollerNames {
        public static final String distanceContoller = "distance contoller"; //TODO make better
    }

    public static final class TaskNames{
        public final static String autoDock = "auto dock";
        public final static String autoGrab = "auto grab";
        public final static String preAutoDrop = "pre auto drop";
        public final static String coneMeasureRanges = "measure cone range";
        public static String autoDrop = "auto drop";
    }

//    public void addAutoGrabPre(TimedTask task, int conePos){
//        task.addStep(() -> triggerEvent(ControllablePart.Events.stopControllers));
//        task.addStep(()-> setGrabberClosed());
//        task.addStep(()-> setLiftPosition(conePos + 400));
//        task.addStep(this::isLiftInTolerance);
//        task.addStep(()->setTurnPosition(.95));
////        task.addStep(() -> setGrabberOpen(true));
//        task.addStep(() -> triggerEvent(ControllablePart.Events.startControllers));
//    }

//    public void addAutoOpenGrabber(TimedTask task){
//        task.addStep(()-> setGrabberOpen(false));
//    }

//    public void addAutoGrabToTask(TimedTask task, int conePos){
//        task.addStep(() -> triggerEvent(Events.stopControllers));
//        task.addStep(() -> setGrabberClosed());
//        task.addStep(() -> setLiftPosition(conePos + 400));
//        task.addStep(this::isLiftInTolerance);
//        task.addStep(() -> setTurnPosition(0.95));
//        //task.addDelay(2000);
//        task.addStep(() -> setGrabberOpen(false));
//        task.addDelay(500); // needed to let grabber open
//        task.addStep(() -> setLiftPosition(conePos));
//        task.addStep(this::isLiftInTolerance);
//        //task.addDelay(2000);
//        task.addStep(() -> setGrabberClosed());
//        task.addDelay(500); // needed to let grabber close
//        task.addStep(() -> setLiftPosition(conePos + 400));
//        task.addStep(this::isLiftInTolerance);
//        task.addStep(() -> triggerEvent(Events.startControllers));
//    }

    public void constructConeRanging(){
        coneRangeingTask.addStep(() -> {
            getHardware().leftUltrasonic.measureRange();
            double thisDist = getHardware().midUltrasonic.getDistanceCm();
            midDist = (thisDist == -1) ? (150) : (thisDist); // when out of range report max
        });
        coneRangeingTask.addDelay(30);
        coneRangeingTask.addStep(() -> {
            getHardware().rightUltrasonic.measureRange();
            double thisDist = getHardware().leftUltrasonic.getDistanceCm();
            leftDist = (thisDist == -1) ? (150) : (thisDist); // when out of range report max
        });
        coneRangeingTask.addDelay(30);
        coneRangeingTask.addStep(() -> {
            getHardware().midUltrasonic.measureRange();
            double thisDist = getHardware().rightUltrasonic.getDistanceCm();
            rightDist = (thisDist == -1) ? (150) : (thisDist); // when out of range report max
        });
        coneRangeingTask.addDelay(30);
        coneRangeingTask.autoReset = true;
        coneRangeingTask.autoStart = true;
    }

    // Line - shaped sensors
    public void doConeRange(DriveControl control) {
        int startDist = 25;
        int finalDist = 15;
        int tolerance = 2;

        if (getLiftPosition() > 400 && (getLeftUltra() < startDist || getMidUltra() < startDist || getRightUltra() < startDist)) {
            // looking for middle side to side
            if (getMidUltra() < getRightUltra() && getMidUltra() < getLeftUltra()){
                /* lined up left and right */
            } else if (getLeftUltra() < getMidUltra()) { // pole to the left
                control.power = control.power.addX(0.15); // move to right
            } else if (getRightUltra() < getMidUltra()) { // pole to the right
                control.power = control.power.addX(-0.15); // move to left
            }
            // setting in/out range
            if (getMidUltra() - tolerance > finalDist) {
                control.power = control.power.addY(-0.11);
            } else if (getMidUltra() + tolerance < finalDist) {
                control.power = control.power.addY(0.11);
            } else { /* lined up in/out */ }

        } else { /*Not in close enough to polish position yet */}
    }

    // U - shaped sensor arrangement
    /*
    public void doConeRange(DriveControl control) {
        if (Math.abs(getRightUltra() - getLeftUltra()) <= 2 && getLeftUltra() < 20) {
            if (getMidUltra() < 6) {
                control.power = control.power.addY(-0.11);
                getHardware().blinkin.setPattern(RevBlinkinLedDriver.BlinkinPattern.BLUE);
            }
            else if (getMidUltra() > 9) {
                control.power = control.power.addY(0.11);
                getHardware().blinkin.setPattern(RevBlinkinLedDriver.BlinkinPattern.BLUE);
            } else {
                getHardware().blinkin.setPattern(RevBlinkinLedDriver.BlinkinPattern.GREEN);
            }
//        } else if(getRightUltra() + getLeftUltra() < 40){
//            getHardware().blinkin.setPattern(RevBlinkinLedDriver.BlinkinPattern.YELLOW);
        } else if (getRightUltra() < getLeftUltra() && getRightUltra() <= 10){  //closer to right
            getHardware().blinkin.setPattern(RevBlinkinLedDriver.BlinkinPattern.YELLOW);
            control.power = control.power.addX(0.15);
        } else if (getLeftUltra() < getRightUltra() && getLeftUltra() <= 10){  //closer to left
            getHardware().blinkin.setPattern(RevBlinkinLedDriver.BlinkinPattern.ORANGE);
            control.power = control.power.addX(-0.15);
        } else {
            getHardware().blinkin.setPattern(RevBlinkinLedDriver.BlinkinPattern.RED);
        }
    }
    */
    @Override
    public void onRun(LifterControl control) { //TODO separate keeping lifter motor position from onRun
        liftWithPower(control.lifterPower);
        turnWithPower(control.turningPower);
        //setGrabberPower(control.closePower);
        if(control.close)
            setGrabberClosed();
        else
            setGrabberOpen(false);
    }

    @Override
    public void onBeanLoad() {
        drive = getBeanManager().getBestMatch(Drive.class, false);
    }

    @Override
    public void onSettingsUpdate(LifterSettings lifterSettings) {

    }

    @Override
    public void onHardwareUpdate(LifterHardware lifterHardware) {

    }

    public static final class Events {
        public static final String dockComplete = "DOCK_COMPLETE";
        public static final String grabComplete = "GRAB_COMPLETE";
        public static final String preDropComplete = "PRE_DROP_COMPLETE";
        public static final String dropComplete = "DROP_COMPlETE";
    }

    @Override
    public void onStart() {
        //taking out setturnpos to see if it messes up other steps in autograb
        // setTurnPosition(getSettings().turnServoStartPosition);
        drive.addController(ContollerNames.distanceContoller, (control) -> doConeRange(control));
    }

    @Override
    public void onStop() {
        drive.removeController(ContollerNames.distanceContoller);
    }
}
