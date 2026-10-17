package com.khomo.chinesechess.i18n;

import com.khomo.chinesechess.board.EndReason;
import com.khomo.chinesechess.board.GameOutcome;
import com.khomo.chinesechess.board.GameStatus;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.PieceType;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.match.MatchPhase;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Every user-visible string of the mod, in one place.
 *
 * <p>The rules package deliberately carries no translation keys; this class owns the mapping from
 * game values to {@link Component}s, so translating the mod means touching exactly one file plus the
 * language resources.</p>
 *
 * <p>Single responsibility: turn game values into text.</p>
 */
public final class Translations {

    private static final String PREFIX = "chinese_chess.";

    private Translations() {}

    public static MutableComponent side(Side side) {
        return Component.translatable(PREFIX + "side." + side.name().toLowerCase());
    }

    public static MutableComponent piece(PieceType type, Side side) {
        return Component.translatable(
                PREFIX + "piece." + side.name().toLowerCase() + "." + type.name().toLowerCase());
    }

    public static MutableComponent piece(Piece piece) {
        return piece(piece.type(), piece.side());
    }

    /** The single character printed on a playing piece, for compact chat messages. */
    public static String glyph(PieceType type, Side side) {
        return switch (type) {
            case GENERAL -> side.isRed() ? "帅" : "将";
            case ADVISOR -> side.isRed() ? "仕" : "士";
            case ELEPHANT -> side.isRed() ? "相" : "象";
            case HORSE -> "马";
            case CHARIOT -> "车";
            case CANNON -> "炮";
            case SOLDIER -> side.isRed() ? "兵" : "卒";
        };
    }

    public static MutableComponent status(GameStatus status) {
        return Component.translatable(status.translationKey());
    }

    public static MutableComponent reason(EndReason reason) {
        return Component.translatable(reason.translationKey());
    }

    public static MutableComponent phase(MatchPhase phase) {
        return Component.translatable(phase.translationKey());
    }

    /** A one-line summary of a finished game, for example "红方胜 - 将死". */
    public static MutableComponent outcome(GameOutcome outcome) {
        MutableComponent headline = status(outcome.status());
        if (outcome.reason() == null) {
            return headline;
        }
        return headline.copy().append(Component.literal(" - ")).append(reason(outcome.reason()));
    }

    /** A piece of chat feedback produced by the interaction layer. */
    public static MutableComponent message(String key, Object... arguments) {
        return Component.translatable(PREFIX + key, arguments);
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }
}
