package dev.lordfetznschaedl.entities;

/**
 * One line of the scoreboard.
 *
 * @param player         the player
 * @param team           side the player was on at the end of the match
 * @param kills          enemy kills
 * @param deaths         deaths
 * @param assists        assists
 * @param headshotKills  kills by headshot
 * @param damage         total damage dealt to enemies
 * @param utilityDamage  damage dealt with grenades
 * @param enemiesFlashed number of enemies flashed
 * @param mvps           round MVP awards
 * @param score          in-game score
 * @param tripleKills    rounds with 3 kills
 * @param quadKills      rounds with 4 kills
 * @param aces           rounds with 5 kills
 * @param roundsPlayed   rounds of the match (used for per-round averages)
 */
public record ScoreboardEntry(
        Player player,
        Team team,
        int kills,
        int deaths,
        int assists,
        int headshotKills,
        int damage,
        int utilityDamage,
        int enemiesFlashed,
        int mvps,
        int score,
        int tripleKills,
        int quadKills,
        int aces,
        int roundsPlayed) {

    /** Average damage per round. */
    public double adr() {
        return roundsPlayed == 0 ? 0 : (double) damage / roundsPlayed;
    }

    /** Kill/death ratio (kills if the player never died). */
    public double killDeathRatio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    /** Share of kills that were headshots, in percent. */
    public double headshotPercentage() {
        return kills == 0 ? 0 : 100.0 * headshotKills / kills;
    }

    /** Kills minus deaths. */
    public int killDeathDifference() {
        return kills - deaths;
    }
}
