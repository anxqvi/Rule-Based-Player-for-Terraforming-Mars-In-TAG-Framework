package evaluation;

import core.AbstractParameters;
import core.AbstractPlayer;
import core.Game;
import core.ParameterFactory;
import core.interfaces.IGameListener;
import core.interfaces.IStatisticLogger;
import games.GameType;
import players.PlayerFactory;
import players.simple.RandomPlayer;
import utilities.FileStatsLogger;
import utilities.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static utilities.Utils.getArg;

public class GameReport {

    public static boolean debug = false;

    /**
     * The idea here is that we get statistics from the decisions of a particular agent in
     * a game, or set of games
     *
     * @param args
     */
    public static void main(String[] args) {
        List<String> argsList = Arrays.asList(args);
        if (argsList.contains("--help") || argsList.contains("-h") || argsList.size() == 0) {
            System.out.println(
                    "To run this class, you can supply a number of possible arguments:\n" +
                            "\tgames=         A list of the games to be played. If there is more than one, then use a \n" +
                            "\t               pipe-delimited list, for example games=Uno|ColtExpress|Pandemic.\n" +
                            "\t               The default is 'all' to indicate that all games should be analysed.\n" +
                            "\t               Specifying all|-name1|-name2... will run all games except for name1, name2...\n" +
                            "\tplayer=        The JSON file containing the details of the Player to monitor, OR\n" +
                            "\t               one of mcts|rmhc|random|osla|<className>. The default is 'random'.\n" +
                            "\topponent=      (Optional) JSON file containing the details of the Player to monitor, OR\n" +
                            "\t               one of mcts|rmhc|random|osla|<className>.\n" +
                            "\t               If not specified then *all* of the players use the same agent type as specified\n" +
                            "\t               with the previous parameter.\n" +
                            "\tgameParam=     (Optional) A JSON file from which the game parameters will be initialised.\n" +
                            "\tnPlayers=      The total number of players in each game (the default is 'all') \n " +
                            "\t               A range can also be specified, for example 3-5. \n " +
                            "\t               Different player counts can be specified for each game in pipe-delimited format.\n" +
                            "\t               If 'all' is specified, then every possible playerCount for the game will be analysed.\n" +
                            "\tnGames=        The number of games to run for each game type. Defaults to 1000.\n" +
                            "\tlistener=      The full class name of an IGameListener implementation. Or the location\n" +
                            "\t               of a json file from which a listener can be instantiated.\n" +
                            "\t               Defaults to utilities.GameStatisticsListener. \n" +
                            "\t               A pipe-delimited string can be provided to gather many types of statistics \n" +
                            "\t               from the same set of games.\n" +
                            "\tlogger=        The full class name of an IStatisticsLogger implementation.\n" +
                            "\t               This is ignored if a json file is provided for the listener.\n" +
                            "\t               Defaults to utilities.SummaryLogger. \n" +
                            "\tlogFile=       Will be used as the IStatisticsLogger log file (FileStatsLogger only)\n" +
                            "\t               A pipe-delimited list should be provided if each distinct listener should\n" +
                            "\t               use a different log file.\n" +
                            "\tstatsLog=      (Optional) If specified this file will be used to log statistics generated by the\n" +
                            "\t               agent's decision making process (e.g. MCTS node count, depth, etc)."
            );
            return;
        }

        // Get Player to be used
        String playerDescriptor = getArg(args, "player", "");
        String opponentDescriptor = getArg(args, "opponent", "random");
        String gameParams = getArg(args, "gameParam", "");
        String loggerClass = getArg(args, "logger", "utilities.SummaryLogger");
        String statsLog = getArg(args, "statsLog", "");
        List<String> listenerClasses = new ArrayList<>(Arrays.asList(getArg(args, "listener", "utilities.GameStatisticsListener").split("\\|")));
        List<String> logFiles = new ArrayList<>(Arrays.asList(getArg(args, "logFile", "GameReport.txt").split("\\|")));

        if (listenerClasses.size() > 1 && logFiles.size() > 1 && listenerClasses.size() != logFiles.size())
            throw new IllegalArgumentException("Lists of log files and listeners must be the same length");

        int nGames = getArg(args, "nGames", 1000);
        List<String> tempGames = new ArrayList<>(Arrays.asList(getArg(args, "games", "all").split("\\|")));
        List<String> games = tempGames;
        if (tempGames.get(0).equals("all")) {
            games = Arrays.stream(GameType.values()).map(Enum::name).filter(name -> !tempGames.contains("-" + name)).collect(toList());
        }

        if (!gameParams.equals("") && games.size() > 1)
            throw new IllegalArgumentException("Cannot yet provide a gameParams argument if running multiple games");

        // This creates a <MinPlayer, MaxPlayer> Pair for each game#
        List<Pair<Integer, Integer>> nPlayers = Arrays.stream(getArg(args, "nPlayers", "all").split("\\|"))
                .map(str -> {
                    if (str.contains("-")) {
                        int hyphenIndex = str.indexOf("-");
                        return new Pair<>(Integer.valueOf(str.substring(0, hyphenIndex)), Integer.valueOf(str.substring(hyphenIndex + 1)));
                    } else if (str.equals("all")) {
                        return new Pair<>(-1, -1); // this is later interpreted as "All the player counts"
                    } else
                        return new Pair<>(Integer.valueOf(str), Integer.valueOf(str));
                }).collect(toList());

        // if only one game size was provided, then it applies to all games in the list
        if (games.size() == 1 && nPlayers.size() > 1) {
            for (int loop = 0; loop < nPlayers.size() - 1; loop++)
                games.add(games.get(0));
        }
        if (nPlayers.size() > 1 && nPlayers.size() != games.size())
            throw new IllegalArgumentException("If specified, then nPlayers length must be one, or match the length of the games list");

        List<IGameListener> gameTrackers = new ArrayList<>();
        for (int i = 0; i < listenerClasses.size(); i++) {
            String logFile = logFiles.size() == 1 ? logFiles.get(0) : logFiles.get(i);
            String listenerClass = listenerClasses.size() == 1 ? listenerClasses.get(0) : listenerClasses.get(i);
            IStatisticLogger logger = IStatisticLogger.createLogger(loggerClass, logFile);
            IGameListener gameTracker = IGameListener.createListener(listenerClass, logger);
            gameTrackers.add(gameTracker);
        }

        IStatisticLogger statsLogger = IStatisticLogger.createLogger(loggerClass, statsLog);

        // Then iterate over the Game Types
        for (int gameIndex = 0; gameIndex < games.size(); gameIndex++) {
            GameType gameType = GameType.valueOf(games.get(gameIndex));

            Pair<Integer, Integer> playerCounts = nPlayers.size() == 1 ? nPlayers.get(0) : nPlayers.get(gameIndex);
            int minPlayers = playerCounts.a > -1 ? playerCounts.a : gameType.getMinPlayers();
            int maxPlayers = playerCounts.b > -1 ? playerCounts.b : gameType.getMaxPlayers();
            for (int playerCount = minPlayers; playerCount <= maxPlayers; playerCount++) {
                System.out.printf("Game: %s, Players: %d\n", gameType.name(), playerCount);
                if (gameType.getMinPlayers() > playerCount) {
                    System.out.printf("Skipping game - minimum player count is %d%n", gameType.getMinPlayers());
                    continue;
                }
                if (gameType.getMaxPlayers() < playerCount) {
                    System.out.printf("Skipping game - maximum player count is %d%n", gameType.getMaxPlayers());
                    continue;
                }

                AbstractParameters params = ParameterFactory.createFromFile(gameType, gameParams);
                Game game = params == null ?
                        gameType.createGameInstance(playerCount) :
                        gameType.createGameInstance(playerCount, params);
                for (IGameListener gameTracker : gameTrackers)
                    game.addListener(gameTracker);

                if (playerDescriptor.isEmpty() && opponentDescriptor.isEmpty()) {
                    opponentDescriptor = "random";
                }
                List<AbstractPlayer> allPlayers = new ArrayList<>();
                for (int j = 0; j < playerCount; j++) {
                    if (opponentDescriptor.isEmpty() || (j == 0 && !playerDescriptor.isEmpty())) {
                        allPlayers.add(PlayerFactory.createPlayer(playerDescriptor));
                        if (!statsLog.isEmpty()) {
                            allPlayers.get(j).setStatsLogger(statsLogger);
                        }
                    } else {
                        allPlayers.add(PlayerFactory.createPlayer(opponentDescriptor));
                    }
                }
                long startingSeed = params == null ? System.currentTimeMillis() : params.getRandomSeed();
                for (int i = 0; i < nGames; i++) {
                    // Run games, resetting the player each time
                    // we also randomise the position of the players for each game
                    Collections.shuffle(allPlayers);
                    game.reset(allPlayers, startingSeed + i);
                    game.run();
                    if (debug) {
                        System.out.printf("Game %4d finished at %tT%n", i, System.currentTimeMillis());
                        System.out.printf("\tResult: %20s%n", game.getPlayers().stream().map(Objects::toString).collect(Collectors.joining(" | ")));
                        System.out.printf("\tResult: %20s%n", Arrays.stream(game.getGameState().getPlayerResults()).map(Objects::toString).collect(Collectors.joining(" | ")));
                    }
                }
            }
        }

        // Once all games are complete, let the gameTracker know
        for (IGameListener gameTracker : gameTrackers) {
            gameTracker.allGamesFinished();
        }
        if (statsLogger != null)
            statsLogger.processDataAndFinish();
    }
}


