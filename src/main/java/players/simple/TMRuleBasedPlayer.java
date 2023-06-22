package players.simple;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import core.actions.DrawCard;
import core.components.*;
import games.terraformingmars.*;
import games.terraformingmars.actions.*;
import games.terraformingmars.components.*;
import games.terraformingmars.rules.requirements.Requirement;
import games.terraformingmars.TMTypes;
import utilities.Vector2D;

import java.util.*;

public class TMRuleBasedPlayer extends AbstractPlayer {

    Random random;

    private enum GameStage {
        EARLY_GAME,
        MID_GAME,
        LATE_GAME
    }

    public TMRuleBasedPlayer() {
        random = new Random();
    }

    @Override
    public AbstractAction getAction(AbstractGameState gameState, List<AbstractAction> possibleActions) {
        TMGameState gs = (TMGameState) gameState;
        TMGameState.TMPhase gamePhase = (TMGameState.TMPhase) gameState.getGamePhase();

        switch (gamePhase) {
            case CorporationSelect:
                return corporationSelect(gs, possibleActions);
            case Research:
                return research(gs, possibleActions);
            case Actions:
                return actions(gs, possibleActions);
            default:
                return possibleActions.get(random.nextInt(possibleActions.size()));
        }
    }

    private AbstractAction corporationSelect(TMGameState gameState, List<AbstractAction> possibleActions) {
        int highestPriority = Integer.MAX_VALUE;
        AbstractAction bestCorporation = null;

        // Priority list of corporations using String names
        List<String> corporationPriority = Arrays.asList(
                "ecoline", "tharsis republic", "helion", "mining guild",
                "thorgate", "phoblog", "inventrix",
                "credicor", "united nations mars initiative", "interplanetary cinematrics",
                "teractor", "saturn systems"
        );

        // Loop through all possible actions to find the best corporation based on the priority list
        for (AbstractAction action : possibleActions) {
            if (action instanceof BuyCard) {
                BuyCard buyCard = (BuyCard) action;
                TMCard card = (TMCard) gameState.getComponentById(buyCard.getCardID());
                String corporationName = card.getComponentName().toLowerCase();
                int priority = corporationPriority.indexOf(corporationName);

                if (priority < highestPriority) {
                    highestPriority = priority;
                    bestCorporation = action;
                }
            }
        }

        if (bestCorporation != null) {
            return bestCorporation;
        }

        // If no valid action was selected, resort to selecting a random possible action
        return possibleActions.get(random.nextInt(possibleActions.size()));
    }

    private AbstractAction research(TMGameState gameState, List<AbstractAction> possibleActions) {
        double budgetRatio = 0.5;
        int playerID = getPlayerID();
        HashMap<TMTypes.Resource, Counter> resources = gameState.getPlayerResources()[playerID];
        int megaCredits = resources.get(TMTypes.Resource.MegaCredit).getValue();
        double budget = megaCredits * budgetRatio;
        Deck<TMCard> cardChoiceDeck = gameState.getPlayerCardChoice()[playerID];
        List<TMCard> cardChoices = new ArrayList<>(cardChoiceDeck.getComponents());

        // Calculate scores for each card in the research phase
        Map<TMCard, Double> cardScores = new HashMap<>();
        for (TMCard card : cardChoices) {
            double score = evaluateCard(card, gameState);
            cardScores.put(card, score);
        }

        List<TMCard> cardPriorityList = new ArrayList<>(cardChoices);
        cardPriorityList.sort((card1, card2) -> Double.compare(cardScores.get(card2), cardScores.get(card1)));

        TMCard currentCard = null;
        BuyCard buyCard = null;
        DiscardCard discardCard = null;

        for (AbstractAction action : possibleActions) {
            if (action instanceof BuyCard) {
                buyCard = (BuyCard) action;
                currentCard = (TMCard) gameState.getComponentById(buyCard.getCardID());
            } else if (action instanceof DiscardCard) {
                discardCard = (DiscardCard) action;
            }
        }

        if (currentCard != null) {
            double score = cardScores.get(currentCard);
            int cardPriority = cardPriorityList.indexOf(currentCard);
            double remainingBudget = budget - 3; // Fixed card cost

            boolean canBuyHigherPriorityCards = true;
            for (int i = 0; i < cardPriority; i++) {
                if (3 > remainingBudget) { // Fixed card cost
                    canBuyHigherPriorityCards = false;
                    break;
                } else {
                    remainingBudget -= 3; // Fixed card cost
                }
            }

            if (score > 0 && canBuyHigherPriorityCards) {
                return buyCard;
            } else {
                return discardCard;
            }
        }

        // If no valid action was selected, resort to selecting a random possible action
        return possibleActions.get(random.nextInt(possibleActions.size()));
    }

    private AbstractAction actions(TMGameState gameState, List<AbstractAction> possibleActions) {
        AbstractAction bestAction = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (AbstractAction action : possibleActions) {
            double score = evaluateAction(action, gameState);
            if (score > bestScore) {
                bestScore = score;
                bestAction = action;
            }
        }

        //System.out.println("Actions action: " + possibleActions + " Best Action: " + bestAction);
        //System.out.println("Best action: " + bestAction);

        // If no valid action was selected, resort to selecting a random possible action
        return bestAction != null ? bestAction : possibleActions.get(random.nextInt(possibleActions.size()));
    }

    /**
     * Evaluates the given action based on its type and the game state
     * by assigning it a score with the highest score being the best action
     *
     * @param card      - the action to evaluate
     * @param gameState - the current state of the Terraforming Mars game state
     * @return          - a score for the given card
     */
    private double evaluateCard(TMCard card, TMGameState gameState) {
        double score = 0;
        double milestone;
        int playerID = getPlayerID();
        HashMap<TMTypes.Resource, Counter> resources = gameState.getPlayerResources()[playerID];
        int megaCredits = resources.get(TMTypes.Resource.MegaCredit).getValue();
        HashMap<TMTypes.Resource, Counter> playerProduction = gameState.getPlayerProduction()[playerID];
        int megaCreditProduction = playerProduction.get(TMTypes.Resource.MegaCredit).getValue();
        TMTypes.Tag[] tags = card.getTags();
        GameStage currentGameStage = setGameStage(gameState);

        // Check if the player can afford the card with their current MegaCredits
        if (card.getCost() > megaCredits) {
            return Double.NEGATIVE_INFINITY;
        }

        // Check if the card is cheap
        if (card.getCost() < 13) {
            if (currentGameStage == GameStage.EARLY_GAME) {
                score += 5;
            }
            else {
                score += 2;
            }
        }

        // Check if the player can afford to play the card within a certain number of generations
        int futureMegaCredits = megaCredits + (1 * megaCreditProduction);

        if (futureMegaCredits < card.getCost()) {
            score -= 1;
        }

        // Calculate affordability weight based on the cost of the card and the player's MegaCredits
        double affordabilityWeight = ((double) megaCredits - card.getCost()) / megaCredits;
        score += 2 * affordabilityWeight;

        // Increase economy weight based on the game stage
        double economyWeight = currentGameStage == GameStage.EARLY_GAME ? 1.5 : 1;

        if (card.getImmediateEffects() != null) {
            for (TMAction action : card.getImmediateEffects()) {
                score += evaluateAction(action, gameState) * economyWeight;
            }
        }

        // Weights for tags based on the game stage
        double plantWeight = currentGameStage == GameStage.EARLY_GAME ? 1 : 8;
        double spaceWeight = currentGameStage == GameStage.LATE_GAME ? 10 : 1;
        double scienceWeight = currentGameStage == GameStage.EARLY_GAME ? 1 : 5;
        double buildingWeight = currentGameStage == GameStage.MID_GAME ? 6 : 3;
        double powerWeight = currentGameStage == GameStage.MID_GAME ? 2 : 1;
        double cityWeight = currentGameStage == GameStage.MID_GAME ? 8 : 4;
        double earthWeight = currentGameStage == GameStage.MID_GAME ? 1 : 2;
        double jovianWeight = currentGameStage == GameStage.MID_GAME ? 2 : 1;
        double eventWeight = currentGameStage == GameStage.MID_GAME ? 9 : 4;
        double microbeWeight = currentGameStage == GameStage.MID_GAME ? 2 : 1;
        double animalWeight = currentGameStage == GameStage.MID_GAME ? 6 : 2;

        // Add tag weights to score
        for (TMTypes.Tag tag : tags) {
            switch (tag) {
                case Plant:
                    boolean isCloseToGardener = isCloseToMilestone(gameState, "gardner", 66);
                    milestone = currentGameStage == GameStage.MID_GAME ? (isCloseToGardener ? 100 : 0) : 0;
                    score += plantWeight + milestone;
                    break;
                case Space:
                    score += spaceWeight;
                    break;
                case Science:
                    score += scienceWeight;
                    break;
                case Building:
                    boolean isCloseToBuilder = isCloseToMilestone(gameState, "builder", 75);
                    milestone = currentGameStage == GameStage.MID_GAME ? (isCloseToBuilder ? 1000 : 0) : 0;
                    score += buildingWeight + milestone;
                    break;
                case Power:
                    score += powerWeight;
                    break;
                case City:
                    boolean isCloseToMayor = isCloseToMilestone(gameState, "mayor", 66);
                    milestone = currentGameStage == GameStage.MID_GAME ? (isCloseToMayor ? 1000 : 0) : 0;
                    score += cityWeight + milestone;
                    break;
                case Earth:
                    score += earthWeight;
                    break;
                case Jovian:
                    score += jovianWeight;
                    break;
                case Event:
                    score += eventWeight;
                    break;
                case Microbe:
                    score += microbeWeight;
                    break;
                case Animal:
                    score += animalWeight;
                    break;

            }
        }

        // Check if card requirements are met
        if (card.getRequirements() != null) {
            for (Requirement requirement : card.getRequirements()) {
                if (!requirement.testCondition(gameState)) {
                    if (gameState.getGeneration() > 0 && gameState.getGeneration() < 4) {
                        score -= 500;
                    }
                    score -= 20;
                    break;
                } else {
                    score += 50;
                }
            }
        }

        return score;
    }

    /**
     * Evaluate the given action based on the type of action and the game state
     * by assigning it a score with the highest score being the most desirable action
     *
     * @param action    - the action to evaluate
     * @param gameState - the current state of the Terraforming Mars game state
     * @return          - a score for the given action
     */
    private double evaluateAction(AbstractAction action, TMGameState gameState) {
        double score = 0;
        int playerID = getPlayerID();
        int nPlayers = gameState.getNPlayers();
        boolean isWinning = isPlayerWinning(playerID, gameState);
        GameStage currentGameStage = setGameStage(gameState);
        HashMap<TMTypes.Resource, Counter> resources = gameState.getPlayerResources()[playerID];
        int megaCredits = resources.get(TMTypes.Resource.MegaCredit).getValue();
        GlobalParameter temperature = gameState.getGlobalParameters().get(TMTypes.GlobalParameter.Temperature);
        boolean temperatureMaxed = temperature.getValue() == temperature.getMaximum();
        GlobalParameter oxygen = gameState.getGlobalParameters().get(TMTypes.GlobalParameter.Oxygen);
        boolean oxygenMaxed = oxygen.getValue() == temperature.getMaximum();

        // Calculate total score for all inner actions wrapped in ChoiceAction
        if (action instanceof ChoiceAction) {
            ChoiceAction choiceAction = (ChoiceAction) action;
            double totalScore = 0;
            for (TMAction singleAction : choiceAction.actions) {
                totalScore += evaluateAction(singleAction, gameState);
            }
            return totalScore;
        }

        // Calculate total score for all inner actions wrapped in CompoundAction
        else if (action instanceof CompoundAction) {
            CompoundAction compoundAction = (CompoundAction) action;
            double totalScore = 0;
            for (TMAction singleAction : compoundAction.actions) {
                totalScore += evaluateAction(singleAction, gameState);
            }
            return totalScore;
        }

        // Calculate total score for all inner actions wrapped in PayForAction
        else if (action instanceof PayForAction) {
            PayForAction payForAction = (PayForAction) action;
            return score + evaluateAction(payForAction.action, gameState);
        }

        // Evaluate the card being played using evaluateCard and additional weighting
        else if (action instanceof PlayCard) {
            PlayCard playCardAction = (PlayCard) action;
            TMCard card = (TMCard) gameState.getComponentById(playCardAction.getPlayCardID());
            double cardScore = evaluateCard(card, gameState);

            if (cardScore > 0) {
                return cardScore + 1000;
            } else {
                return Double.NEGATIVE_INFINITY;
            }
        }

        // Calculate the score for modifying global parameters (oxygen/temperature)
        else if (action instanceof ModifyGlobalParameter) {
            ModifyGlobalParameter modifyGlobalParameterAction = (ModifyGlobalParameter) action;
            TMTypes.GlobalParameter globalParameter = modifyGlobalParameterAction.param;
            boolean isCloseToTerraformer = isCloseToMilestone(gameState, "terraformer", 75);
            double milestone = currentGameStage == GameStage.MID_GAME ? (isCloseToTerraformer ? 100 : 0) : 0;

            if (globalParameter == TMTypes.GlobalParameter.Temperature) {
                double weight = nPlayers < 4 ? (isWinning && currentGameStage == GameStage.LATE_GAME ? 400 : 0) : 100;
                double playerWeight = nPlayers > 3 ? 150 : 0;
                double maxCheck = temperatureMaxed ? -1500 : 0;
                return score + weight + milestone + playerWeight + maxCheck;
            } else if (globalParameter == TMTypes.GlobalParameter.Oxygen) {
                double weight = nPlayers < 4 ? (isWinning && currentGameStage == GameStage.LATE_GAME ? 400 : 0) : 100;
                double playerWeight = nPlayers > 3 ? 150 : 0;
                double maxCheck = oxygenMaxed ? -1500 : 0;
                return score + weight + milestone + playerWeight + maxCheck;
            }
        }

        // Calculate the score for modifying player resources and card draw
        else if (action instanceof ModifyPlayerResource) {
            ModifyPlayerResource modifyPlayerResourceAction = (ModifyPlayerResource) action;
            TMTypes.Resource resourceType = modifyPlayerResourceAction.resource;
            boolean isProduction = modifyPlayerResourceAction.production;
            double resourceChange = modifyPlayerResourceAction.change;

            double resourceScore = 0;
            switch (resourceType) {
                case MegaCredit:
                    if (currentGameStage != GameStage.LATE_GAME) {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 1000 : -50) : (resourceChange > 0 ? 300 : 150)) + resourceChange;
                    } else {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 50 : 100) : (resourceChange > 0 ? 300 : 450)) - resourceChange;
                    }
                    break;
                case Heat:
                    if (!temperatureMaxed) {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 15 : 0) : (resourceChange > 0 ? 0 : 150)) - resourceChange;
                    } else {
                        resourceScore = (isProduction ? (resourceChange > -1500 ? 15 : 0) : (resourceChange > 0 ? -500 : 0)) - resourceChange;
                    }
                    break;
                case Energy:
                    if (!temperatureMaxed) {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 5 : 0) : (resourceChange > 0 ? 0 : 40)) - resourceChange;
                    } else {
                        resourceScore = (isProduction ? (resourceChange > 0 ? -1000 : 0) : (resourceChange > 0 ? -300 : 0)) - resourceChange;
                    }
                    break;
                case Plant:
                    boolean isCloseToGardener = isCloseToMilestone(gameState, "gardner", 66);
                    double milestone = currentGameStage == GameStage.MID_GAME ? (isCloseToGardener ? 100 : 0) : 0;
                    resourceScore = (isProduction ? (resourceChange > 0 ? 200 : 0) : (resourceChange > 0 ? 150 : 300)) + milestone + resourceChange;
                    break;
                case TR:
                    resourceScore = (nPlayers > 3 ? (resourceChange > 0 ? 600 : 0) : 100) + resourceChange;
                    break;
                case Titanium:
                    if (currentGameStage != GameStage.LATE_GAME) {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 500 : 0) : (resourceChange > 0 ? 400 : 0)) + resourceChange;
                    } else {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 20 : 100) : (resourceChange > 0 ? 25 : 300)) - resourceChange;
                    }
                    break;
                case Steel:
                    if (currentGameStage != GameStage.LATE_GAME) {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 600 : 0) : (resourceChange > 0 ? 400 : 0)) + resourceChange;
                    } else {
                        resourceScore = (isProduction ? (resourceChange > 0 ? 20 : 110) : (resourceChange > 0 ? 25 : 300)) - resourceChange;
                    }
                    break;
                case Card:
                    resourceScore = 1200;
                default:
                    break;
            }
            return score + resourceScore;
        }

        // Calculate the score for placing greenery, ocean, and city tiles
        else if (action instanceof PlaceTile) {
            PlaceTile placeTileAction = (PlaceTile) action;
            int mapTileID = placeTileAction.mapTileID;
            TMMapTile tileToPlace = (TMMapTile) gameState.getComponentById(mapTileID);
            TMTypes.Tile tileType = placeTileAction.tile;
            if (tileType == TMTypes.Tile.Greenery) {
                double threshold = nPlayers > 3 ? 0 : 66;
                boolean isCloseToGardener = isCloseToMilestone(gameState, "gardner", threshold);
                double milestone = currentGameStage != GameStage.EARLY_GAME ? (isCloseToGardener ? 10000 : 0) : 0;
                double weight = nPlayers > 3 ? (currentGameStage != GameStage.EARLY_GAME ? 200 : 0) :
                        (currentGameStage != GameStage.EARLY_GAME ? 500 : 0);
                if (tileToPlace == null) {
                    return score + weight + milestone;
                }
                return score + 80 + weight + milestone + evaluatePlaceGreeneryAction(placeTileAction, gameState);
            } else if (tileType == TMTypes.Tile.Ocean) {
                double weight = currentGameStage != GameStage.EARLY_GAME ? 150 : 50;
                if (tileToPlace == null) {
                    return score + weight;
                }
                return score + 80 + weight + evaluatePlaceOceanAction(placeTileAction, gameState);
            } else if (tileType == TMTypes.Tile.City) {
                double weight = nPlayers > 3 ? (currentGameStage != GameStage.EARLY_GAME ? 100 : 0) :
                        (currentGameStage != GameStage.EARLY_GAME ? 200 : 0);
                double threshold = nPlayers > 3 ? 0 : 66;
                boolean isCloseToMayor = isCloseToMilestone(gameState, "mayor", threshold);
                double milestone = currentGameStage != GameStage.EARLY_GAME ? (isCloseToMayor ? 10000 : 0) : 0;
                if (tileToPlace == null) {
                    return score + weight + milestone;
                }
                return score + 20 + milestone + evaluatePlaceCityAction(placeTileAction, gameState);
            }
        }

        // Calculate the score for claiming a milestone or funding an award
        else if (action instanceof ClaimAwardMilestone) {
            ClaimAwardMilestone claimAwardMilestoneAction = (ClaimAwardMilestone) action;
            TMTypes.ActionType actionType = claimAwardMilestoneAction.actionType;
            if (actionType == TMTypes.ActionType.ClaimMilestone) {
                return score + 80000000;
            } else if (actionType == TMTypes.ActionType.FundAward) {
                int toClaimID = claimAwardMilestoneAction.getToClaimID();
                Award award = (Award) gameState.getComponentById(toClaimID);
                Award closestAward = getWinningAward(gameState);
                if (award != null && award.equals(closestAward) && currentGameStage == GameStage.LATE_GAME) {
                    return score + 1500;
                }
            }
        }

        // Calculate the score for passing
        else if (action.getClass().equals(TMAction.class)) {
            for (Milestone m : gameState.getMilestones()) {
                if (m.canClaim(gameState, getPlayerID()) && megaCredits < 8) {
                    return score + 1000000;
                }
            }
            return score - 10;
        }

        // This action seems to be bugged for all players, so I have disabled it
        else if (action instanceof TopCardDecision) {
            return Double.NEGATIVE_INFINITY;
        }

        // Calculate the score for buying a card during the action phase only
        else if (action instanceof BuyCard) {
            BuyCard buyCard = (BuyCard) action;
            TMCard card = (TMCard) gameState.getComponentById(buyCard.getCardID());
            if (card.getCost() <= megaCredits) {
                double tempScore = evaluateCard(card, gameState);
                if (tempScore > 0) {
                    return score + 50;
                }
                else {
                    return score - 50;
                }
            }
        }

        // Calculate the score for discarding a card during the action phase only
        else if (action instanceof DiscardCard) {
            DiscardCard discardCard = (DiscardCard) action;
            TMCard card = (TMCard) gameState.getComponentById(discardCard.getCardID());
            if (card.getCost() <= megaCredits) {
                double tempScore = evaluateCard(card, gameState);
                if (tempScore > 0) {
                    return score - 50;
                }
                else {
                    return score + 50;
                }
            }
        }

        // Calculate the score for adding animals, microbes, and science to a card
        else if (action instanceof AddResourceOnCard) {
            AddResourceOnCard addResourceOnCard = (AddResourceOnCard) action;
            TMTypes.Resource resource = addResourceOnCard.resource;
            if (resource == TMTypes.Resource.Animal) { // Check if the player can afford the card
                if (currentGameStage == GameStage.EARLY_GAME && addResourceOnCard.amount < 0) {
                    return score - 100;
                } else {
                    return score + 20;
                }
            } else if (resource == TMTypes.Resource.Microbe) {
                if (currentGameStage == GameStage.EARLY_GAME && addResourceOnCard.amount < 0) {
                    return score - 100;
                } else {
                    return score + 10;
                }
            } else if (resource == TMTypes.Resource.Science) {
                if (currentGameStage == GameStage.EARLY_GAME && addResourceOnCard.amount < 0) {
                    return score - 100;
                } else {
                    return score + 15;
                }
            }
        }

        // Calculate the score for drawing cards
        else if (action instanceof DrawCard) {
            return score + 10000;
        }

        return score;
    }

    /**
     * Evaluates a PlaceTile action for a greenery tile and returns a score
     *
     * @param placeTileAction      - the player's PlaceTile action that they are evaluating
     * @param gameState            - the current state of the Terraforming Mars game state
     * @return                     - a score for PlaceTile action for a potential map tile
     */
    private double evaluatePlaceGreeneryAction(PlaceTile placeTileAction, TMGameState gameState) {
        int mapTileID = placeTileAction.mapTileID;
        TMMapTile tileToPlace = (TMMapTile) gameState.getComponentById(mapTileID);
        double score = 0;

        if (tileToPlace == null || tileToPlace.getTileType() != TMTypes.MapTileType.Ground) {
            return Double.NEGATIVE_INFINITY;
        }

        List<Vector2D> neighbours = PlaceTile.getNeighbours(new Vector2D(tileToPlace.getX(), tileToPlace.getY()));
        for (Vector2D neighbour : neighbours) {
            TMMapTile neighbourTile = gameState.getBoard().getElement(neighbour.getX(), neighbour.getY());
            if (neighbourTile != null) {
                TMTypes.Tile neighbourTileType = neighbourTile.getTilePlaced();
                if (neighbourTileType == TMTypes.Tile.City) {
                    int owner = neighbourTile.getOwnerId();
                    if (owner == getPlayerID()) {
                        score += 800;
                    } else if (owner >= 0){
                        score -= 500;
                    }
                } else if (neighbourTileType == TMTypes.Tile.Ocean) {
                    score += 1;
                }
            }
        }

        // Check for plant resources on the map tile
        TMTypes.Resource[] resources = tileToPlace.getResources();
        for (TMTypes.Resource resource : resources) {
            if (resource == TMTypes.Resource.Plant) {
                score += 10;
                break;
            }
        }

        return score;
    }

    /**
     * Evaluates a PlaceTile action for a city tile and returns a score
     *
     * @param placeTileAction      - the player's PlaceTile action that they are evaluating
     * @param gameState            - the current state of the Terraforming Mars game state
     * @return                     - a score for PlaceTile action for a potential map tile
     */
    private double evaluatePlaceCityAction(PlaceTile placeTileAction, TMGameState gameState) {
        int cityCount = 0;
        double score = 0;
        int mapTileID = placeTileAction.mapTileID;
        TMMapTile tileToPlace = (TMMapTile) gameState.getComponentById(mapTileID);

        if (tileToPlace == null || tileToPlace.getTileType() != TMTypes.MapTileType.Ground) {
            return Double.NEGATIVE_INFINITY;
        }

        int x = tileToPlace.getX();
        int y = tileToPlace.getY();

        // Get the board dimensions from the TMGameState object
        GridBoard<TMMapTile> gridBoard = gameState.getBoard();
        int boardWidth = gridBoard.getWidth();
        int boardHeight = gridBoard.getHeight();

        // Check if the city is placed on the edge of the map
        boolean isXEdge = (x == 0 || x == boardWidth - 1);
        boolean isYEdge = (y == 0 || y == boardHeight - 1);

        // Check if the tile is placed on an edge or a corner
        if (isXEdge && isYEdge) {
            score -= 200; // Corner
        } else if (isXEdge || isYEdge) {
            score -= 150; // Edge
        }

        List<Vector2D> neighbours = PlaceTile.getNeighbours(new Vector2D(x, y));
        int adjacentGreeneryCount = 0;
        int adjacentGroundCount = 0;
        for (Vector2D neighbour : neighbours) {
            TMMapTile neighbourTile = gameState.getBoard().getElement(neighbour.getX(), neighbour.getY());
            if (neighbourTile != null) {
                TMTypes.Tile neighbourTileType = neighbourTile.getTilePlaced();
                TMTypes.MapTileType neighbourMapTileType = neighbourTile.getMapTileType();
                if (neighbourTileType == TMTypes.Tile.Greenery) {
                    adjacentGreeneryCount++;
                }
                if (neighbourMapTileType == TMTypes.MapTileType.Ground) {
                    adjacentGroundCount++;
                }
            }
        }

        // Count the number of cities placed by the player
        for (TMMapTile mapTile : gameState.getBoard().getComponents()) {
            if (mapTile != null && mapTile.getTilePlaced() == TMTypes.Tile.City && mapTile.getOwnerId() == gameState.getCurrentPlayer()) {
                cityCount++;
            }
        }

        // Check if the player has already placed the maximum number of cities
        if (cityCount >= 4) {
            return Double.NEGATIVE_INFINITY;
        }

        score += adjacentGroundCount * 2; // Increase the score based on the number of surrounding tiles
        score += adjacentGreeneryCount * 20; // Increase the score more for adjacent greeneries

        return score;
    }

    /**
     * Evaluate a PlaceTile action for an ocean tile and returns a score
     *
     * @param placeTileAction      - the player's PlaceTile action that they are evaluating
     * @param gameState            - the current state of the Terraforming Mars game state
     * @return                     - score for PlaceTile action for a potential map tile
     */
    private double evaluatePlaceOceanAction(PlaceTile placeTileAction, TMGameState gameState) {
        int mapTileID = placeTileAction.mapTileID;
        TMMapTile tileToPlace = (TMMapTile) gameState.getComponentById(mapTileID);
        double score = 0;

        if (tileToPlace == null) {
            return Double.NEGATIVE_INFINITY;
        }

        List<Vector2D> neighbourPositions = PlaceTile.getNeighbours(new Vector2D(tileToPlace.getX(), tileToPlace.getY()));
        int adjacentPlayerTiles = 0;
        int adjacentOpponentTiles = 0;
        for (Vector2D neighbourPosition : neighbourPositions) {
            TMMapTile adjacentTile = gameState.getBoard().getElement(neighbourPosition.getX(), neighbourPosition.getY());
            if (adjacentTile != null && adjacentTile.getTilePlaced() != null) {
                if (adjacentTile.getOwnerId() == getPlayerID()) {
                    adjacentPlayerTiles += 1;
                } else {
                    adjacentOpponentTiles += 1;
                }
            }
        }

        score += 2 * adjacentPlayerTiles - adjacentOpponentTiles;

        return score;
    }

    /**
     * Set the game stage based on the current generation and number of players
     *
     * @param gameState - the current state of the Terraforming Mars game state
     * @return          - the current game stage
     */
    private GameStage setGameStage(TMGameState gameState) {
        double currentGeneration = gameState.getGeneration();
        int nPlayers = gameState.getNPlayers();
        double totalGenerations = nPlayers < 4 ? 12 : 12;

        if (currentGeneration <= (int) (totalGenerations / 3)) {
            return GameStage.EARLY_GAME;
        } else if (currentGeneration <= (int) (totalGenerations * (2/3))) {
            return GameStage.MID_GAME;
        } else {
            return GameStage.LATE_GAME;
        }
    }

    /**
     * Checks if the player is close to claiming a Milestone based on a threshold percentage
     *
     * @param gameState           - the current state of the Terraforming Mars game state
     * @param milestoneName       - the String name of targeted Milestone
     * @param thresholdPercentage - the threshold value to check player progress against
     * @return                    - boolean value for if player can claim Milestone
     */
    private boolean isCloseToMilestone(TMGameState gameState, String milestoneName, double thresholdPercentage) {
        int claimedMilestones = 0;
        Milestone milestone = null;

        for (Milestone m : gameState.getMilestones()) {
            if (m.isClaimed()) {
                claimedMilestones++;
            }
            if (m.getComponentName().equalsIgnoreCase(milestoneName)) {
                milestone = m;
            }
        }

        if (milestone == null) {
            return false;
        }

        if (milestone.isClaimed()) {
            return false;
        }

        if (claimedMilestones >= 3) {
            return false;
        }

        int count = milestone.checkProgress(gameState, getPlayerID());
        double progressPercentage = (double) count / milestone.min * 100;

        return progressPercentage >= thresholdPercentage;
    }

    /**
     * Check if the player is currently leading in an award as well as
     * the difference between the player and the runner-up to determine
     * the least contested award
     *
     * @param gameState - the current state of the Terraforming Mars game state
     * @return          - Award if player is leader by a minimum difference otherwise null
     */
    private Award getWinningAward(TMGameState gameState) {
        Award winningAward = null;
        int maxDifference = Integer.MIN_VALUE;
        int threshold = 5;

        for (Award award : gameState.getAwards()) {
            if (!award.isClaimed() && award.canClaim(gameState, getPlayerID())) {
                int currentPlayerProgress = award.checkProgress(gameState, getPlayerID());
                int maxOpponentProgress = 0;
                boolean playerIsWinning = true;

                for (int i = 0; i < gameState.getNPlayers(); i++) {
                    if (i == getPlayerID()) continue;
                    int opponentProgress = award.checkProgress(gameState, i);
                    maxOpponentProgress = Math.max(maxOpponentProgress, opponentProgress);

                    if (opponentProgress >= currentPlayerProgress) {
                        playerIsWinning = false;
                        break;
                    }
                }

                if (playerIsWinning) {
                    int difference = currentPlayerProgress - maxOpponentProgress;
                    if (difference > maxDifference && difference <= threshold) {
                        maxDifference = difference;
                        winningAward = award;
                    }
                }
            }
        }
        return winningAward;
    }


    /**
     * Check if the targeted player is winning by comparing its score to other players
     *
     * @param playerID  - ID number of the targeted player
     * @param gameState - the current state of the Terraforming Mars game state
     * @return          - boolean value for if player is currently winning
     */
    public boolean isPlayerWinning(int playerID, TMGameState gameState) {
        double currentPlayerScore = gameState.countPoints(playerID);
        boolean isWinning = false;

        for (int i = 0; i < gameState.getNPlayers(); i++) {
            if (i == playerID) {
                continue;
            }
            if (gameState.countPoints(i) >= currentPlayerScore) {
                isWinning = false;
                break;
            }
            else {
                isWinning = true;
            }
        }

        return isWinning;
    }

    /** Return the class name as a string */
    public String toString() {
        return "TMRuleBasedPlayer";
    }

    /** Copy the player object */
    @Override
    public AbstractPlayer copy() { return this; }

}