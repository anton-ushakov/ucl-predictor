package com.example.predictor.domain;

/**
 * Когда участник видит чужие прогнозы на матч, пока приём прогнозов открыт. После закрытия приёма
 * на матч чужие прогнозы видят все участники группы в любом режиме.
 */
public enum RevealMode {

    AFTER_OWN_SUBMIT("Сразу после того, как сохранён свой прогноз на этот матч, остальным после закрытия приёма"),
    AT_LOCK("Только после закрытия приёма прогнозов на матч (до этого не видит никто)");

    private final String description;

    RevealMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
