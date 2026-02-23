package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.physics.SimulationState;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.part.Control;
import cam72cam.immersiverailroading.physics.MovementTrack;
import cam72cam.immersiverailroading.registry.LocomotiveElectricDefinition;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.mod.energy.Energy;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.serialization.TagField;

import java.util.List;
import java.util.OptionalDouble;

public class LocomotiveElectric extends Locomotive {

	private float relativeRPM;
	private int turnOnOffDelay = 0;

	@TagSync
	@TagField("TURNED_ON")
	private boolean turnedOn = false;

	@TagSync
	@TagField("INTERNAL_BATTERY")
	private Energy energy = new Energy(0, 0);

	@TagSync
	@TagField
	private boolean isOnPoweredTrack = false;

	private int throttleCooldown;
	private int reverserCooldown;

	public LocomotiveElectric() {

	}

	@Override
	public int getInventoryWidth() {
		return getDefinition().isCabCar() ? 0 : 2;
	}
	
	public void setTurnedOn(boolean value) {
		turnedOn = value;
		setControlPositions(ModelComponentType.ENGINE_START_X, turnedOn ? 1 : 0);
	}
	
	public boolean isTurnedOn() {
		return turnedOn;
	}

	public boolean isRunning() {
		if (!Config.isFuelRequired(gauge)) {
			return isTurnedOn();
		}
		return isTurnedOn() && this.getBatteryAmount() > 0;
	}
	
	@Override
	public LocomotiveElectricDefinition getDefinition() {
		return super.getDefinition(LocomotiveElectricDefinition.class);
	}
	
	@Override
	public boolean openGui(Player player) {
		//TODO
//		if (!getDefinition().isCabCar() && player.hasPermission(Permissions.LOCOMOTIVE_CONTROL)) {
//			GuiTypes.ELECTRIC_LOCOMOTIVE.open(player, this);
//			return true;
//		}
		return false;
	}

	/*
	 * Sets the throttle or brake on all connected diesel locomotives if the throttle or brake has been changed
	 */
	@Override
	public void handleKeyPress(Player source, KeyTypes key, boolean disableIndependentThrottle) {
		switch(key) {
			case START_STOP_ENGINE:
				if (turnOnOffDelay == 0) {
					turnOnOffDelay = 10;
					setTurnedOn(!isTurnedOn());
				}
				break;
			case REVERSER_UP:
			case REVERSER_ZERO:
			case REVERSER_DOWN:
				if (this.reverserCooldown > 0) {
					return;
				}
				reverserCooldown = 3;
				super.handleKeyPress(source, key, disableIndependentThrottle);
				break;
			case THROTTLE_UP:
			case THROTTLE_ZERO:
			case THROTTLE_DOWN:
				if (this.throttleCooldown > 0) {
					return;
				}
				throttleCooldown = 2;
				super.handleKeyPress(source, key, disableIndependentThrottle);
				break;
			default:
				super.handleKeyPress(source, key, disableIndependentThrottle);
		}
	}

	@Override
	public float getThrottleDelta() {
		return 1F / this.getDefinition().getThrottleNotches();
	}

	@Override
	public boolean providesElectricalPower() {
		return this.isRunning();
	}

    @Override
	protected float getReverserDelta() {
		return 0.51f;
	}

	@Override
	public void setThrottle(float newThrottle) {
		int targetNotch = Math.round(newThrottle / getThrottleDelta());
		//issue #1526: when dragging or control with augment throttle glitches
		super.setThrottle(targetNotch * getThrottleDelta());
	}

	@Override
	public void setReverser(float newReverser) {
		super.setReverser(Math.round(newReverser));

	}

	@Override
	public double getAppliedTractiveEffort(Speed speed) {
		if (isRunning() && (/*getEngineTemperature() > 75 ||*/ !Config.isFuelRequired(gauge))) {
			double maxPower_W = this.getDefinition().getHorsePower(gauge) * 745.7d;
			double efficiency = 0.82; // Similar to a *lot* of imperial references
			double speed_M_S = (Math.abs(speed.metric())/3.6);
			double maxPowerAtSpeed = maxPower_W * efficiency / Math.max(0.001, speed_M_S);
			double applied = maxPowerAtSpeed * relativeRPM * getReverser();
			if (getDefinition().hasDynamicTractionControl) {
				double max = getStaticTractiveEffort(speed);
				if (Math.abs(applied) > max) {
					return Math.copySign(max, applied) * 0.95;
				}

			}
			return applied;
		}
		return 0;
	}

	@Override
	public void onTick() {
		super.onTick();

		if (turnOnOffDelay > 0) {
			turnOnOffDelay -= 1;
		}

		float absThrottle = Math.abs(this.getThrottle());
		if (this.relativeRPM > absThrottle) {
			this.relativeRPM -= Math.min(0.01f, this.relativeRPM - absThrottle);
		} else if (this.relativeRPM < absThrottle) {
			this.relativeRPM += Math.min(0.01f, absThrottle - this.relativeRPM);
		}
		if (getWorld().isClient) {
			return;
		}

		OptionalDouble control = this.getDefinition().getModel().getControls().stream()
				.filter(x -> x.part.type == ModelComponentType.HORN_CONTROL_X)
				.mapToDouble(this::getControlPosition)
				.max();
		if (control.isPresent() && control.getAsDouble() > 0) {
			this.setHorn(10, hornPlayer);
		}

		if (throttleCooldown > 0) {
			throttleCooldown--;
		}

		if (reverserCooldown > 0) {
			reverserCooldown--;
		}

		//Take 1RF as 200J
		if (getDefinition().isCog() && getTickCount() % 20 == 0) {
			SimulationState state = getCurrentState();
			if (state != null) {
				ITrack found = MovementTrack.findTrack(getWorld(), state.couplerPositionFront, state.yaw, gauge.value());
				if (found instanceof TileRailBase) {
					TileRailBase onTrack = (TileRailBase) found;
					isOnPoweredTrack = onTrack.isElectricPowered();
				}
			}
		}

		if (isOnPoweredTrack) {
			this.energy.receive(1000, false);
		}

		if (isTurnedOn() && !Config.ConfigBalance.FuelRequired) {
			if (this.getBatteryAmount() > 0) {
				int consumption = (getDefinition().getHorsePower(gauge) * 745) / 200 / 20;

				this.energy.extract(consumption, false);
				if (this.energy.getCurrent() == 0) {
					setTurnedOn(false);
				}
			} else {
				setTurnedOn(false);
			}
		}
	}

	public int getBatteryCapacity() {
		return getDefinition().getBatteryCapacity(this.gauge);
	}

	public int getBatteryAmount() {
		return energy.getCurrent();
	}

	public float getBatteryPercentage() {
		return ((float) energy.getCurrent()) / energy.getMax();
	}
	
	@Override
	public List<Fluid> getFluidFilter() {
		return BurnUtil.burnableFluids();
	}

	@Override
	public FluidQuantity getTankCapacity() {
		return FluidQuantity.ZERO;
	}

	@Override
	public void onAssemble() {
		super.onAssemble();
		this.energy = new Energy(0, getBatteryCapacity());
	}
	
	@Override
	public void onDissassemble() {
		super.onDissassemble();
		setTurnedOn(false);
		this.energy = null;
	}

	public float getRelativeRPM() {
		return relativeRPM;
	}

	@Override
	public void onDragRelease(Control<?> component) {
		super.onDragRelease(component);
		if (component.part.type == ModelComponentType.ENGINE_START_X) {
			turnedOn = getDefinition().getModel().getControls().stream()
					.filter(c -> c.part.type == ModelComponentType.ENGINE_START_X)
					.allMatch(c -> getControlPosition(c) == 1);
		}
		if (component.part.type == ModelComponentType.REVERSER_X) {
			// Make sure reverser is sync'd
			setControlPositions(ModelComponentType.REVERSER_X, getReverser()/-2 + 0.5f);
		}
	}
}