package poker;

/**
 * Constantes et configuration du jeu de Poker Indien.
 * Centralise tous les parametres ajustables du jeu.
 */
public final class GameConfig {

    private GameConfig() {}

    // --- Cartes ---
    public static final int CARD_MIN           = 1;
    public static final int CARD_MAX           = 13;
    public static final int HIGH_CARD_THRESHOLD = 8;   // >= 8 = high_opponent_card

    // --- Jetons ---
    public static final int STARTING_STACK     = 50;
    public static final int ANTE               = 1;

    // --- Seuils de decision ---
    public static final int STRONG_BET_THRESHOLD = 5;  // >= 5 = strong_opponent_bet
    public static final int BIG_POT_THRESHOLD    = 15; // >= 15 = big_pot
    public static final int LOW_STACK_THRESHOLD  = 10; // <= 10 = low_stack

    // --- Temps (ms) ---
    public static final long FAST_RESPONSE_MS  = 500;
    public static final long SLOW_RESPONSE_MS  = 2000;
    public static final long DELAY_BETWEEN_ROUNDS_MS = 1500;
    public static final long STARTUP_DELAY_MS  = 2000;

    // --- Protocole ACL ---
    public static final String PREFIX_DEAL     = "DEAL";
    public static final String PREFIX_ACTION   = "ACTION";
    public static final String PREFIX_RESULT   = "RESULT";
    public static final String SEPARATOR       = "|";

    // --- Noms des agents ---
    public static final String AGENT1_NAME     = "Agent1";
    public static final String AGENT2_NAME     = "Agent2";
}
