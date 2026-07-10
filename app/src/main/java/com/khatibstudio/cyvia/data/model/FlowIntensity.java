package com.khatibstudio.cyvia.data.model;

/**
 * Represents the intensity of menstrual flow for a given cycle or daily log.
 */
public enum FlowIntensity {

    /** Minimal spotting, not a full period day */
    SPOTTING,

    /** Light flow */
    LIGHT,

    /** Moderate / typical flow */
    MEDIUM,

    /** Heavy flow */
    HEAVY,

    /** Very heavy flow */
    VERY_HEAVY
}
