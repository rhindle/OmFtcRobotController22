package org.firstinspires.ftc.teamcode.parts.lifter;

import org.apache.commons.lang3.ObjectUtils;
import org.firstinspires.ftc.teamcode.parts.lifter.settings.LifterTeleopSettings;

import om.self.ezftc.core.part.LoopedPartImpl;
import om.self.supplier.suppliers.EdgeSupplier;
import om.self.task.core.Group;
import om.self.task.core.TaskEx;

public class LifterTeleop extends LoopedPartImpl<Lifter, LifterTeleopSettings, ObjectUtils.Null> {
    private LifterTeleopSettings settings;

    private EdgeSupplier preDropEdge = new EdgeSupplier();

    public LifterTeleop(Lifter parent) {
        super(parent, "lifter teleop");
        setSettings(LifterTeleopSettings.makeDefault(parent.parent));
    }

    public LifterTeleop(Lifter parent, LifterTeleopSettings settings) {
        super(parent, "lifter teleop");
        setSettings(settings);
    }

    public LifterTeleopSettings getSettings() {
        return settings;
    }

    public void setSettings(LifterTeleopSettings settings) {
        this.settings = settings;
    }

    @Override
    public void onBeanLoad() {}

    @Override
    public void onInit() {
        preDropEdge.setBase(() -> settings.preDropSupplier.get() > -1);
    }

    @Override
    public void onStart() {
        parent.setBaseController(() -> new LifterControl(
                (double) settings.heightSpeedSupplier.get(),
                (double) settings.turnSpeedSupplier.get() * settings.turnSpeedMultiplier,
                settings.grabberCloseSupplier.get()
        ), true);
    }

    @Override
    public void onRun() {
        parent.setCone(settings.coneChangeSupplier.get() + parent.getCone());

        if(preDropEdge.isRisingEdge()){
            parent.setPole(settings.preDropSupplier.get());
            ((TaskEx) parent.getTaskManager().getChild(Lifter.TaskNames.preAutoDrop)).restart();
        }
        else if(settings.autoGrabSupplier.get())
            ((TaskEx) parent.getTaskManager().getChild(Lifter.TaskNames.autoGrab)).restart();
        else if(settings.autoDockSupplier.get())
            ((TaskEx) parent.getTaskManager().getChild(Lifter.TaskNames.autoDock)).restart();
        else if(settings.autoDropSupplier.get())
            ((TaskEx) parent.getTaskManager().getChild(Lifter.TaskNames.autoDrop)).restart();

        parent.parent.opMode.telemetry.addData("cone", parent.getCone());
    }

    @Override
    public void onStop() {
        parent.setBaseControllerToDefault(parent.isControlActive());
    }
}