package com.team.ms51sim.ui;

import com.team.ms51sim.CPU;
import com.team.ms51sim.Simulator;

/** In-process backend: the original single-process simulator (used for the baseline). */
public final class LocalBackend implements SimulatorBackend {
    private final Simulator sim = new Simulator();

    @Override public void load(int[] code) { sim.load(code); }
    @Override public void reset() { sim.reset(); }
    @Override public Simulator.StepResult step() { return sim.step(); }
    @Override public CPU cpu() { return sim.cpu(); }
    @Override public boolean finished() { return sim.finished(); }
}
