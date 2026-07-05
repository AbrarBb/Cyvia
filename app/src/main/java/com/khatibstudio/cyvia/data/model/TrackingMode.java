package com.khatibstudio.cyvia.data.model;

/**
 * The user's current tracking goal.
 * This affects which predictions and messaging are shown.
 *
 * IMPORTANT: If mode is NOT_TRACKING_PREGNANCY or NO_PERIODS_CONTRACEPTION,
 * the PredictionEngine must suppress all fertile-window and pregnancy-related output.
 *
 * If mode is PERIMENOPAUSE or POSTPARTUM, predictions are shown with a
 * low-confidence caveat rather than false precision.
 */
public enum TrackingMode {

    /** Standard cycle tracking — show all predictions */
    REGULAR,

    /** Cycles are irregular — show predictions with wider confidence range */
    IRREGULAR,

    /** Actively trying to conceive — highlight fertile window */
    TRYING_TO_CONCEIVE,

    /** Avoiding pregnancy — show fertile window (user is aware) */
    AVOIDING_PREGNANCY,

    /** On hormonal contraception — no fertile window or pregnancy messaging */
    NO_PERIODS_CONTRACEPTION,

    /** Postpartum — suppress predictions or show with very wide range */
    POSTPARTUM,

    /** Approaching menopause — wide prediction range, explicit caveat */
    PERIMENOPAUSE
}
