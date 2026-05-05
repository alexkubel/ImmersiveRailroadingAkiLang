package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.LocomotiveElectric;
import cam72cam.immersiverailroading.gui.overlay.GuiBuilder;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.ValveGearConfig;
import cam72cam.immersiverailroading.model.ElectricLocomotiveModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.util.DataBlock;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.resource.Identifier;

import java.io.IOException;

public class LocomotiveElectricDefinition extends LocomotiveDefinition {
    public SoundDefinition idle;
    public SoundDefinition running;
    public SoundDefinition horn;
    private int batteryCapacity;
    private boolean hornSus;
    private int notches;
    private float enginePitchRange;
    public boolean hasDynamicTractionControl;

    public LocomotiveElectricDefinition(String defID, DataBlock data) throws Exception {
        super(LocomotiveElectric.class, defID, data);
    }

    @Override
    protected Identifier defaultDataLocation() {
        return new Identifier(ImmersiveRailroading.MODID, "rolling_stock/default/electric.caml");
    }

    @Override
    public void loadData(DataBlock data) throws Exception {
        super.loadData(data);

        DataBlock properties = data.getBlock("properties");
        if (!isCabCar()) {
            batteryCapacity = (int) (properties.getValue("battery_capacity_rf").asInteger() * internal_inv_scale);
            hasDynamicTractionControl = properties.getValue("dynamic_traction_control").asBoolean();
        } else {
            batteryCapacity = 0;
        }
        notches = properties.getValue("throttle_notches").asInteger();

        hornSus = properties.getValue("horn_sustained").asBoolean();

        DataBlock sounds = data.getBlock("sounds");
        idle = SoundDefinition.getOrDefault(sounds, "idle");
        running = SoundDefinition.getOrDefault(sounds, "running");
        enginePitchRange = sounds.getValue("engine_pitch_range").asFloat();
        horn = SoundDefinition.getOrDefault(sounds, "horn");
        bell = SoundDefinition.getOrDefault(sounds, "bell");
    }

    @Override
    protected StockModel<?, ?> createModel() throws Exception {
        return new ElectricLocomotiveModel(this);
    }

    @Override
    protected GuiBuilder getDefaultOverlay(DataBlock data) throws IOException {
        return readCabCarFlag(data) ?
                GuiBuilder.parse(new Identifier(ImmersiveRailroading.MODID, "gui/default/cab_car.caml")) :
                GuiBuilder.parse(new Identifier(ImmersiveRailroading.MODID, "gui/default/electric.caml"));
    }

    @Override
    public StockModel<?, ?> getModel() {
        return (ElectricLocomotiveModel) super.getModel();
    }

    //checks to see if horn is sustained, on by default
    public boolean getHornSus() {
        return hornSus;
    }

    public int getBatteryCapacity(Gauge gauge) {
        return (int) (batteryCapacity * gauge.scale());
    }

    public ValveGearConfig getValveGear() {
        return super.getValveGear() == null ? new ValveGearConfig(ValveGearConfig.ValveGearType.CONNECTING, null) : super.getValveGear();
    }

    public int getThrottleNotches() {
        return notches;
    }

    public float getEnginePitchRange() {
        return enginePitchRange;
    }
}
