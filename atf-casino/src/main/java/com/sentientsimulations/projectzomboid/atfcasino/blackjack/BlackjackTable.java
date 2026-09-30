package com.sentientsimulations.projectzomboid.atfcasino.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative multiplayer blackjack table. Pure state machine: no PZ types, no threads, no
 * wall clock — every call takes {@code now} (ms) so the driver decides cadence and tests are
 * deterministic. Money moves only through the {@link Bank} port; the engine never trusts a client
 * number beyond the bet amount it validates against {@link Limits}.
 *
 * <p>Round shape: {@link Phase#BETTING} (opens a {@link Limits#betWindowMs()} countdown at the
 * first bet, or starts at once when every seated player has bet) → {@link Phase#PLAYING} (seats act
 * in order, {@link Limits#actionMs()} per hand, timeout = stand) → dealer draws to 17 (stands on
 * soft 17) and pays → {@link Phase#SETTLE} for {@link Limits#settleMs()} → back to betting. Seated
 * players who don't bet simply sit the round out. Leaving mid-hand auto-stands; the hand still
 * settles and any payout goes to the seat's recorded identity, so walking away never voids a
 * winning hand.
 *
 * <p>Table rules: blackjack pays 3:2, dealer stands on soft 17. Double on any first two cards,
 * including after a split. A pair (any two ten-value cards count) may be split once; each half gets
 * one card at once, split aces take exactly one card, and 21 on a split hand pays even money rather
 * than as a natural. Late surrender on an unsplit first two cards returns half the stake; a dealer
 * natural is settled before anyone acts, so there is nothing to surrender against.
 */
public final class BlackjackTable {

    public static final int MAX_SEATS = 5;
    public static final int MAX_HANDS = 2;
    public static final long BET_WINDOW_MS = 20_000L;
    public static final long ACTION_MS = 20_000L;
    public static final long SETTLE_MS = 8_000L;

    public enum Phase {
        BETTING,
        PLAYING,
        SETTLE
    }

    public enum Outcome {
        NONE,
        WIN,
        BLACKJACK,
        PUSH,
        LOSE,
        BUST,
        SURRENDER
    }

    public enum Action {
        OK,
        TABLE_FULL,
        ALREADY_SEATED,
        NOT_SEATED,
        NOT_BETTING_PHASE,
        ALREADY_BET,
        BET_TOO_LOW,
        BET_TOO_HIGH,
        BANK_REFUSED,
        NOT_YOUR_TURN,
        CANNOT_DOUBLE,
        CANNOT_SPLIT,
        CANNOT_SURRENDER
    }

    /** Result of a player request; {@code detail} carries the bank's refusal reason if any. */
    public record Result(Action action, @Nullable String detail) {
        public static final Result OK = new Result(Action.OK, null);

        static Result of(Action action) {
            return new Result(action, null);
        }

        public boolean ok() {
            return action == Action.OK;
        }
    }

    /** Economy port. {@link #take} returns null on success or a refusal reason. */
    public interface Bank {
        @Nullable
        String take(String username, long steamId, int amount, String reason);

        void give(String username, long steamId, int amount, String reason);
    }

    /** Table rules read fresh on every use so sandbox edits apply without a restart. */
    public interface Limits {
        int minBet();

        int maxBet();

        default long betWindowMs() {
            return BET_WINDOW_MS;
        }

        default long actionMs() {
            return ACTION_MS;
        }

        default long settleMs() {
            return SETTLE_MS;
        }
    }

    /** One wager and the cards played against it. A seat holds two of these after a split. */
    public static final class PlayerHand {
        final Hand cards = new Hand();
        int bet;
        boolean doubled;
        boolean stood;
        boolean split;
        boolean surrendered;
        Outcome outcome = Outcome.NONE;
        int payout;

        public Hand cards() {
            return cards;
        }

        public int bet() {
            return bet;
        }

        public boolean isDoubled() {
            return doubled;
        }

        public boolean isSplit() {
            return split;
        }

        public boolean isSurrendered() {
            return surrendered;
        }

        public Outcome outcome() {
            return outcome;
        }

        public int payout() {
            return payout;
        }

        /** Two-card 21 pays 3:2 only when the hand was dealt that way, never after a split. */
        public boolean isNatural() {
            return !split && cards.isBlackjack();
        }

        boolean live() {
            return bet > 0 && !stood && !surrendered && cards.total() < 21;
        }

        boolean standing() {
            return bet > 0 && !surrendered && !cards.isBust() && !isNatural();
        }
    }

    public static final class Seat {
        final int index;
        final String username;
        final long steamId;
        final List<PlayerHand> hands = new ArrayList<>();
        boolean leaving;

        Seat(int index, String username, long steamId) {
            this.index = index;
            this.username = username;
            this.steamId = steamId;
            hands.add(new PlayerHand());
        }

        public int index() {
            return index;
        }

        public String username() {
            return username;
        }

        public long steamId() {
            return steamId;
        }

        public List<PlayerHand> hands() {
            return Collections.unmodifiableList(hands);
        }

        /** The first hand's cards — the whole seat until it splits. */
        public Hand hand() {
            return hands.get(0).cards;
        }

        /** Total staked across every hand this round. */
        public int bet() {
            int total = 0;
            for (PlayerHand h : hands) {
                total += h.bet;
            }
            return total;
        }

        public Outcome outcome() {
            return hands.get(0).outcome;
        }

        /** Total paid out across every hand this round. */
        public int payout() {
            int total = 0;
            for (PlayerHand h : hands) {
                total += h.payout;
            }
            return total;
        }

        public boolean isLeaving() {
            return leaving;
        }

        boolean inPlay() {
            return hands.get(0).bet > 0;
        }

        boolean live() {
            for (PlayerHand h : hands) {
                if (h.live()) {
                    return true;
                }
            }
            return false;
        }

        void reset() {
            hands.clear();
            hands.add(new PlayerHand());
        }
    }

    private static final String STAKE_REASON = "casino_stake_blackjack";
    private static final String DOUBLE_REASON = "casino_double_blackjack";
    private static final String SPLIT_REASON = "casino_split_blackjack";
    private static final String PAYOUT_REASON = "casino_payout_blackjack";
    private static final String REFUND_REASON = "casino_refund_blackjack";

    private final Bank bank;
    private final Limits limits;
    private final Shoe shoe;
    private final Seat[] seats = new Seat[MAX_SEATS];
    private final Hand dealer = new Hand();

    private Phase phase = Phase.BETTING;
    private long deadline;
    private int currentSeat = -1;
    private int currentHand;
    private boolean holeHidden = true;
    private int round;
    private boolean dirty;
    private final List<String> log = new ArrayList<>();

    public BlackjackTable(Bank bank, Limits limits, Random rng) {
        this.bank = bank;
        this.limits = limits;
        this.shoe = new Shoe(rng);
    }

    // --- queries ---

    public Phase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public int currentSeat() {
        return currentSeat;
    }

    /**
     * Index into the acting seat's {@link Seat#hands()}; meaningful only while a seat is acting.
     */
    public int currentHand() {
        return currentHand;
    }

    public Hand dealerHand() {
        return dealer;
    }

    public boolean isHoleHidden() {
        return holeHidden;
    }

    public long deadline() {
        return deadline;
    }

    public int secondsLeft(long now) {
        if (deadline == 0L) {
            return 0;
        }
        return (int) Math.max(0L, (deadline - now + 999L) / 1000L);
    }

    public @Nullable Seat seat(int index) {
        return index >= 0 && index < MAX_SEATS ? seats[index] : null;
    }

    public @Nullable Seat seatOf(String username) {
        for (Seat s : seats) {
            if (s != null && s.username.equalsIgnoreCase(username)) {
                return s;
            }
        }
        return null;
    }

    public List<Seat> seatedPlayers() {
        List<Seat> out = new ArrayList<>();
        for (Seat s : seats) {
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    public boolean hasFreeSeat() {
        for (Seat s : seats) {
            if (s == null) {
                return true;
            }
        }
        return false;
    }

    /** True when the seat may double right now: their turn, acting hand on its first two cards. */
    public boolean canDouble(Seat seat) {
        PlayerHand h = acting(seat);
        return h != null && h.cards.size() == 2 && !h.doubled;
    }

    /** True when the acting hand is an unsplit pair (ten-value cards pair with each other). */
    public boolean canSplit(Seat seat) {
        PlayerHand h = acting(seat);
        if (h == null || seat.hands.size() >= MAX_HANDS || h.cards.size() != 2) {
            return false;
        }
        List<Card> c = h.cards.cards();
        return c.get(0).value() == c.get(1).value();
    }

    /** True on the seat's first decision of the round: two cards, nothing split or doubled. */
    public boolean canSurrender(Seat seat) {
        PlayerHand h = acting(seat);
        return h != null && seat.hands.size() == 1 && h.cards.size() == 2 && !h.doubled;
    }

    /** Drain-and-clear of human-readable events since the last call (for the client's log line). */
    public List<String> drainLog() {
        List<String> out = new ArrayList<>(log);
        log.clear();
        return out;
    }

    /** True if state changed since the last {@link #clearDirty()}; drivers use it to broadcast. */
    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    // --- player requests ---

    public Result sit(String username, long steamId) {
        if (seatOf(username) != null) {
            return Result.of(Action.ALREADY_SEATED);
        }
        for (int i = 0; i < MAX_SEATS; i++) {
            if (seats[i] == null) {
                seats[i] = new Seat(i, username, steamId);
                say(username + " sits down");
                dirty = true;
                return Result.OK;
            }
        }
        return Result.of(Action.TABLE_FULL);
    }

    /**
     * Leave the table. During betting an already-placed stake is refunded; mid-hand the seat is
     * marked leaving, auto-stands, and is removed after settlement so its payout still lands.
     */
    public Result leave(String username, long now) {
        Seat seat = seatOf(username);
        if (seat == null) {
            return Result.of(Action.NOT_SEATED);
        }
        dirty = true;
        if (phase == Phase.PLAYING && seat.inPlay()) {
            seat.leaving = true;
            say(username + " leaves the table");
            if (currentSeat == seat.index) {
                for (PlayerHand h : seat.hands) {
                    h.stood = true;
                }
                advance(now);
            }
            return Result.OK;
        }
        if (phase == Phase.BETTING && seat.bet() > 0) {
            bank.give(seat.username, seat.steamId, seat.bet(), REFUND_REASON);
            seat.reset();
        }
        seats[seat.index] = null;
        say(username + " leaves the table");
        if (phase == Phase.BETTING && deadline != 0L && allSeatedHaveBet()) {
            startRound(now);
        }
        return Result.OK;
    }

    public Result bet(String username, int amount, long now) {
        Seat seat = seatOf(username);
        if (seat == null) {
            return Result.of(Action.NOT_SEATED);
        }
        if (phase != Phase.BETTING) {
            return Result.of(Action.NOT_BETTING_PHASE);
        }
        if (seat.inPlay()) {
            return Result.of(Action.ALREADY_BET);
        }
        if (amount < limits.minBet()) {
            return Result.of(Action.BET_TOO_LOW);
        }
        if (amount > limits.maxBet()) {
            return Result.of(Action.BET_TOO_HIGH);
        }
        String refused = bank.take(seat.username, seat.steamId, amount, STAKE_REASON);
        if (refused != null) {
            return new Result(Action.BANK_REFUSED, refused);
        }
        seat.hands.get(0).bet = amount;
        dirty = true;
        say(username + " bets " + amount);
        if (allSeatedHaveBet()) {
            startRound(now);
        } else if (deadline == 0L) {
            deadline = now + limits.betWindowMs();
        }
        return Result.OK;
    }

    public Result hit(String username, long now) {
        Seat seat = seatOf(username);
        if (seat == null) {
            return Result.of(Action.NOT_SEATED);
        }
        PlayerHand hand = acting(seat);
        if (hand == null) {
            return Result.of(Action.NOT_YOUR_TURN);
        }
        hand.cards.add(shoe.draw());
        dirty = true;
        if (hand.cards.isBust()) {
            say(username + handTag(seat) + " busts");
        }
        if (!hand.live()) {
            advance(now);
        } else {
            deadline = now + limits.actionMs();
        }
        return Result.OK;
    }

    public Result stand(String username, long now) {
        Seat seat = seatOf(username);
        if (seat == null) {
            return Result.of(Action.NOT_SEATED);
        }
        PlayerHand hand = acting(seat);
        if (hand == null) {
            return Result.of(Action.NOT_YOUR_TURN);
        }
        hand.stood = true;
        dirty = true;
        say(username + handTag(seat) + " stands on " + hand.cards.total());
        advance(now);
        return Result.OK;
    }

    public Result doubleDown(String username, long now) {
        Seat seat = seatOf(username);
        if (seat == null) {
            return Result.of(Action.NOT_SEATED);
        }
        PlayerHand hand = acting(seat);
        if (hand == null) {
            return Result.of(Action.NOT_YOUR_TURN);
        }
        if (!canDouble(seat)) {
            return Result.of(Action.CANNOT_DOUBLE);
        }
        String refused = bank.take(seat.username, seat.steamId, hand.bet, DOUBLE_REASON);
        if (refused != null) {
            return new Result(Action.BANK_REFUSED, refused);
        }
        hand.bet *= 2;
        hand.doubled = true;
        hand.cards.add(shoe.draw());
        hand.stood = true;
        dirty = true;
        say(username + handTag(seat) + " doubles down" + (hand.cards.isBust() ? " and busts" : ""));
        advance(now);
        return Result.OK;
    }

    /**
     * Split the acting pair into two hands, each staked at the original bet and dealt one card at
     * once. Split aces stand immediately; anything else plays on from the first hand.
     */
    public Result split(String username, long now) {
        Seat seat = seatOf(username);
        if (seat == null) {
            return Result.of(Action.NOT_SEATED);
        }
        PlayerHand first = acting(seat);
        if (first == null) {
            return Result.of(Action.NOT_YOUR_TURN);
        }
        if (!canSplit(seat)) {
            return Result.of(Action.CANNOT_SPLIT);
        }
        String refused = bank.take(seat.username, seat.steamId, first.bet, SPLIT_REASON);
        if (refused != null) {
            return new Result(Action.BANK_REFUSED, refused);
        }
        PlayerHand second = new PlayerHand();
        second.bet = first.bet;
        Card moved = first.cards.removeLast();
        second.cards.add(moved);
        first.split = true;
        second.split = true;
        seat.hands.add(currentHand + 1, second);
        first.cards.add(shoe.draw());
        second.cards.add(shoe.draw());
        if (moved.isAce()) {
            first.stood = true;
            second.stood = true;
        }
        dirty = true;
        say(username + " splits " + (moved.isAce() ? "aces" : "a pair"));
        if (first.live()) {
            deadline = now + limits.actionMs();
        } else {
            advance(now);
        }
        return Result.OK;
    }

    /** Give up the hand for half the stake back. Only on the seat's first two cards. */
    public Result surrender(String username, long now) {
        Seat seat = seatOf(username);
        if (seat == null) {
            return Result.of(Action.NOT_SEATED);
        }
        PlayerHand hand = acting(seat);
        if (hand == null) {
            return Result.of(Action.NOT_YOUR_TURN);
        }
        if (!canSurrender(seat)) {
            return Result.of(Action.CANNOT_SURRENDER);
        }
        hand.surrendered = true;
        hand.stood = true;
        dirty = true;
        say(username + " surrenders");
        advance(now);
        return Result.OK;
    }

    // --- clock ---

    public void tick(long now) {
        switch (phase) {
            case BETTING -> {
                if (deadline != 0L && now >= deadline) {
                    if (anyBets()) {
                        startRound(now);
                    } else {
                        deadline = 0L;
                        dirty = true;
                    }
                }
            }
            case PLAYING -> {
                if (now >= deadline) {
                    Seat seat = currentSeat >= 0 ? seats[currentSeat] : null;
                    if (seat != null) {
                        seat.hands.get(currentHand).stood = true;
                        say(seat.username + handTag(seat) + " ran out of time and stands");
                    }
                    dirty = true;
                    advance(now);
                }
            }
            case SETTLE -> {
                if (now >= deadline) {
                    newRound();
                }
            }
        }
    }

    // --- internals ---

    /** The hand this seat is acting on right now, or null when it is not their turn. */
    private @Nullable PlayerHand acting(Seat seat) {
        if (phase != Phase.PLAYING || currentSeat != seat.index) {
            return null;
        }
        return seat.hands.get(currentHand);
    }

    /** Log suffix that names the hand when a seat has split, e.g. {@code " (hand 2)"}. */
    private String handTag(Seat seat) {
        return seat.hands.size() > 1 ? " (hand " + (currentHand + 1) + ")" : "";
    }

    private boolean anyBets() {
        for (Seat s : seats) {
            if (s != null && s.inPlay()) {
                return true;
            }
        }
        return false;
    }

    private boolean allSeatedHaveBet() {
        boolean any = false;
        for (Seat s : seats) {
            if (s == null) {
                continue;
            }
            if (!s.inPlay()) {
                return false;
            }
            any = true;
        }
        return any;
    }

    private void startRound(long now) {
        round++;
        shoe.reshuffleIfLow();
        dealer.clear();
        holeHidden = true;
        for (Seat s : seats) {
            if (s != null && s.inPlay()) {
                s.hand().clear();
                s.hand().add(shoe.draw());
            }
        }
        dealer.add(shoe.draw());
        for (Seat s : seats) {
            if (s != null && s.inPlay()) {
                s.hand().add(shoe.draw());
            }
        }
        dealer.add(shoe.draw());
        phase = Phase.PLAYING;
        currentSeat = -1;
        currentHand = 0;
        dirty = true;
        say("Round " + round + ": cards dealt");
        if (dealer.isBlackjack()) {
            say("Dealer has blackjack");
            settle(now);
            return;
        }
        advance(now);
    }

    /** Move to the next live hand: the acting seat's later hands first, then the seats after it. */
    private void advance(long now) {
        Seat acting = currentSeat >= 0 ? seats[currentSeat] : null;
        if (acting != null && !acting.leaving) {
            for (int h = currentHand + 1; h < acting.hands.size(); h++) {
                if (acting.hands.get(h).live()) {
                    beginTurn(currentSeat, h, now);
                    return;
                }
            }
        }
        for (int i = currentSeat + 1; i < MAX_SEATS; i++) {
            Seat s = seats[i];
            if (s == null || s.leaving) {
                continue;
            }
            for (int h = 0; h < s.hands.size(); h++) {
                if (s.hands.get(h).live()) {
                    beginTurn(i, h, now);
                    return;
                }
            }
        }
        currentSeat = -1;
        currentHand = 0;
        dealerPlay();
        settle(now);
    }

    private void beginTurn(int seatIndex, int handIndex, long now) {
        currentSeat = seatIndex;
        currentHand = handIndex;
        deadline = now + limits.actionMs();
        dirty = true;
    }

    private void dealerPlay() {
        holeHidden = false;
        boolean anyoneStanding = false;
        for (Seat s : seats) {
            if (s == null) {
                continue;
            }
            for (PlayerHand h : s.hands) {
                anyoneStanding |= h.standing();
            }
        }
        if (!anyoneStanding) {
            return;
        }
        while (dealer.total() < 17) {
            dealer.add(shoe.draw());
        }
    }

    private void settle(long now) {
        holeHidden = false;
        int dealerTotal = dealer.total();
        boolean dealerBj = dealer.isBlackjack();
        boolean dealerBust = dealer.isBust();
        for (Seat s : seats) {
            if (s == null || !s.inPlay()) {
                continue;
            }
            for (int i = 0; i < s.hands.size(); i++) {
                PlayerHand h = s.hands.get(i);
                int total = h.cards.total();
                if (h.surrendered) {
                    h.outcome = Outcome.SURRENDER;
                    h.payout = h.bet / 2;
                } else if (h.cards.isBust()) {
                    h.outcome = Outcome.BUST;
                    h.payout = 0;
                } else if (h.isNatural()) {
                    if (dealerBj) {
                        h.outcome = Outcome.PUSH;
                        h.payout = h.bet;
                    } else {
                        h.outcome = Outcome.BLACKJACK;
                        h.payout = h.bet + (h.bet * 3) / 2;
                    }
                } else if (dealerBj || (!dealerBust && dealerTotal > total)) {
                    h.outcome = Outcome.LOSE;
                    h.payout = 0;
                } else if (dealerBust || total > dealerTotal) {
                    h.outcome = Outcome.WIN;
                    h.payout = h.bet * 2;
                } else {
                    h.outcome = Outcome.PUSH;
                    h.payout = h.bet;
                }
                if (h.payout > 0) {
                    bank.give(s.username, s.steamId, h.payout, PAYOUT_REASON);
                }
                String tag = s.hands.size() > 1 ? " (hand " + (i + 1) + ")" : "";
                say(
                        s.username
                                + tag
                                + ": "
                                + h.outcome.name().toLowerCase()
                                + " ("
                                + h.payout
                                + ")");
            }
        }
        phase = Phase.SETTLE;
        deadline = now + limits.settleMs();
        dirty = true;
    }

    private void newRound() {
        for (int i = 0; i < MAX_SEATS; i++) {
            Seat s = seats[i];
            if (s == null) {
                continue;
            }
            if (s.leaving) {
                seats[i] = null;
            } else {
                s.reset();
            }
        }
        dealer.clear();
        holeHidden = true;
        phase = Phase.BETTING;
        deadline = 0L;
        currentSeat = -1;
        currentHand = 0;
        dirty = true;
    }

    private void say(String message) {
        log.add(message);
    }

    Shoe shoe() {
        return shoe;
    }
}
