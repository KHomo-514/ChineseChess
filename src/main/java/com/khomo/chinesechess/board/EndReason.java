package com.khomo.chinesechess.board;

/**
 * Why a game ended.
 *
 * <p>Each constant carries its own translation key so that the network layer, the chat messages and
 * the GUI all describe the same ending without any duplicated switch statements.</p>
 *
 * <p>Single responsibility: name the causes of a finished game.</p>
 */
public enum EndReason {
    /** 将死 - the side to move is in check and has no legal answer. */
    CHECKMATE("chinese_chess.reason.checkmate"),
    /** 困毙 - the side to move is not in check but has no legal move at all; it still loses. */
    STALEMATE("chinese_chess.reason.stalemate"),
    /** A player gave up through the GUI. */
    RESIGN("chinese_chess.reason.resign"),
    /** 长将 - the same position returned three times while one side kept checking. */
    PERPETUAL_CHECK("chinese_chess.reason.perpetual_check"),
    /** A neutral threefold repetition. */
    REPETITION_DRAW("chinese_chess.reason.repetition"),
    /** 自然限着 - the agreed number of plies passed without a capture. */
    NO_CAPTURE_DRAW("chinese_chess.reason.no_capture"),
    /** 双方均无取胜可能 - no side owns a piece that could ever mate. */
    INSUFFICIENT_MATERIAL_DRAW("chinese_chess.reason.insufficient_material"),
    /**
     * A player left the board while the game was running.
     *
     * <p>Nothing produces this any more: leaving deliberately keeps the position and only frees the
     * seat, so a departure cannot decide a game. The constant stays because the reason travels as an
     * ordinal, and dropping it would renumber every reason after it.</p>
     */
    OPPONENT_LEFT("chinese_chess.reason.opponent_left"),
    /** The board block was destroyed, so the match could not continue. */
    BOARD_DESTROYED("chinese_chess.reason.board_destroyed"),
    /** The match was dropped for housekeeping reasons (idle too long, world shutdown, ...). */
    ABORTED("chinese_chess.reason.aborted");

    private final String translationKey;

    EndReason(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    public static EndReason of(int ordinal) {
        EndReason[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ABORTED;
    }
}
