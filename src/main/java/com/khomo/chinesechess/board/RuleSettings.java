package com.khomo.chinesechess.board;

/**
 * The rule switches a game is played under.
 *
 * <p>Kept free of any mod-loader type so the rules package stays pure and unit-testable; the mod's
 * configuration file is read once, at the boundary, and converted into an instance of this record.</p>
 *
 * <p>Single responsibility: carry the configurable rule parameters.</p>
 */
public record RuleSettings(
        boolean repetitionEnabled,
        boolean perpetualCheckLoses,
        int noCaptureDrawPlies,
        boolean insufficientMaterialDraw) {

    /** Values used when no configuration is available (for example in unit tests). */
    public static final RuleSettings DEFAULT = new RuleSettings(true, true, 120, true);

    public RuleSettings {
        if (noCaptureDrawPlies < 0) {
            noCaptureDrawPlies = 0;
        }
    }

    /** Whether the natural move limit (自然限着) is in force. */
    public boolean hasNoCaptureLimit() {
        return noCaptureDrawPlies > 0;
    }
}
