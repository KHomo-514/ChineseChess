package com.khomo.chinesechess.client.screen;

import com.khomo.chinesechess.Config;
import com.khomo.chinesechess.board.GameOutcome;
import com.khomo.chinesechess.board.GameStatus;
import com.khomo.chinesechess.board.Piece;
import com.khomo.chinesechess.board.PieceType;
import com.khomo.chinesechess.board.Side;
import com.khomo.chinesechess.board.Squares;
import com.khomo.chinesechess.client.ClientMatchSession;
import com.khomo.chinesechess.client.render.BoardGlyphAtlas;
import com.khomo.chinesechess.client.render.BoardSprites;
import com.khomo.chinesechess.i18n.Translations;
import com.khomo.chinesechess.match.MatchPhase;
import com.khomo.chinesechess.match.MatchSnapshot;
import com.khomo.chinesechess.network.MatchFinishedPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The playing surface.
 *
 * <p>The world is blurred <em>behind</em> the board rather than hidden from it: the screen asks for
 * the vanilla background once, at the top of the frame, and then only tints it, so you can still tell
 * where you are standing. The board itself stays opaque - a see-through playing surface costs exactly
 * the contrast the pieces need - and the information strip is dark enough to stay legible over any
 * sky.</p>
 *
 * <p>The one thing here that must not be changed is the drawing order. The blur has to run before the
 * board. {@code Screen#render} calls {@code renderBackground} <em>after</em> the subclass has drawn,
 * so letting it run there blurs the board itself: that is what made the whole screen look out of focus
 * while the vanilla buttons drawn after it stayed sharp.</p>
 *
 * <p>Three details are deliberate and worth keeping:</p>
 * <ul>
 *   <li>Text is scaled by whole numbers only. Minecraft's font is a bitmap atlas; drawing it at 0.85
 *       or 1.3 resamples every glyph and is a large part of what "blurry" usually means in a GUI.</li>
 *   <li>Markers that belong <em>around</em> a point - the legal destinations and the selected piece -
 *       are rings drawn after the pieces, so an occupied destination stays visible instead of being
 *       hidden underneath the piece standing on it.</li>
 *   <li>The board's cells are always square, so the picture matches a real board; the surplus width
 *       of a widescreen window is given to the information strip instead of stretched cells.</li>
 * </ul>
 *
 * <p>Single responsibility: present the board and turn clicks into move requests.</p>
 */
public class XiangqiBoardScreen extends Screen {

    // ---------------------------------------------------------------- palette

    private static final int SCRIM_FILL = 0x47140E08;
    private static final int SCRIM_BOTTOM = 0x3C000000;
    private static final int BAR_FILL = 0xB8160F07;
    private static final int BAR_LINE = 0xCC4A3418;
    private static final int PANEL_SHADOW = 0x14000000;
    private static final int PANEL_FRAME = 0xF23A2610;
    private static final int PANEL_FACE = 0xFFE3C88E;
    private static final int PANEL_GRAIN = 0x1E9C7742;
    private static final int GRID_COLOUR = 0xFF3A2814;

    private static final int TITLE_COLOUR = 0xFFFFE9B0;
    private static final int STATUS_COLOUR = 0xFFEBDDC0;
    private static final int WARN_COLOUR = 0xFFFF766C;
    private static final int LABEL_COLOUR = 0xFFD6C6A8;

    private static final int LAST_MOVE_FILL = 0x66FFB300;
    private static final int SELECT_RING = 0xFFFFE066;
    private static final int TARGET_RING = 0xFF3EDC81;
    private static final int CHECK_RING = 0xFFFF5544;
    private static final int BANNER_FILL = 0xE6100B05;
    private static final int BANNER_EDGE = 0xFFFFE9B0;

    /** File positions, in grid cells, of 楚 河 汉 界 inside the river gap. */
    private static final double[] RIVER_FILE_POSITIONS = {1.5D, 2.5D, 5.5D, 6.5D};
    /**
     * The same four glyphs as seen from BLACK's side.
     *
     * <p>The two phrases trade halves and each one still reads forwards. Mirroring the file of every
     * glyph on its own - which is what the rest of the layout wants - would put 河 before 楚 and 界
     * before 汉, and both phrases would read backwards.</p>
     */
    private static final double[] RIVER_FILE_POSITIONS_FLIPPED = {5.5D, 6.5D, 1.5D, 2.5D};

    private static final int BUTTON_WIDTH = 58;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_RIGHT_INSET = 8;

    // ---------------------------------------------------------------- state

    private final ClientMatchSession session = ClientMatchSession.instance();
    private final BlockPos boardOrigin;

    private BoardGuiLayout layout;
    private boolean layoutFlipped;
    private int lastRevision = -1;
    private int selected = -1;
    private List<Integer> targets = List.of();
    private Button resignButton;
    private Button resetButton;
    private Component banner;
    private int bannerColour = TITLE_COLOUR;
    private boolean leaveRequested;

    public XiangqiBoardScreen(BlockPos boardOrigin) {
        super(Component.translatable("chinese_chess.screen.title"));
        this.boardOrigin = boardOrigin;
    }

    /** The board this screen belongs to; the packet router uses it to ignore other boards' packets. */
    public BlockPos boardOrigin() {
        return boardOrigin;
    }

    @Override
    protected void init() {
        layout = BoardGuiLayout.create(width, height, flipped());
        layoutFlipped = flipped();
        // There is no "leave" button on purpose: escape is the way out, exactly as the game screen it
        // replaces (see removed(), which turns either route into the same single leave request).
        // Both remaining buttons always exist and only their visibility follows the game - see
        // syncWidgets for why.
        resignButton = addRenderableWidget(Button.builder(Translations.message("gui.resign"), button -> resign())
                .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        resetButton = addRenderableWidget(
                Button.builder(Translations.message("gui.reset"), button -> session.requestReset(boardOrigin))
                        .bounds(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build());
        syncWidgets();
    }

    /**
     * Keeps the button row in step with the match.
     *
     * <p>A board screen outlives the state it was opened in: the player who sits down first gets their
     * screen while the board is still {@code WAITING}, so a resign button that was only added when the
     * game was already running would never appear for them - which is exactly what happened to red,
     * whose screen was created while waiting for an opponent and never re-initialised when the game
     * started.</p>
     *
     * <p>The row is laid out from the right inwards, so hiding either button leaves the other at the
     * edge instead of leaving a hole where the hidden one used to be.</p>
     */
    private void syncWidgets() {
        boolean resign = canResign();
        boolean reset = canReset();
        resignButton.visible = resign;
        resignButton.active = resign;
        resetButton.visible = reset;
        resetButton.active = reset;

        int cursor = width - BUTTON_RIGHT_INSET;
        int y = (BoardGuiLayout.TOP_BAR - BUTTON_HEIGHT) / 2;
        resignButton.setX(cursor - BUTTON_WIDTH);
        resignButton.setY(y);
        if (resign) {
            cursor -= BUTTON_WIDTH + BUTTON_GAP;
        }
        resetButton.setX(cursor - BUTTON_WIDTH);
        resetButton.setY(y);
    }

    /**
     * Re-derives the highlighted destinations whenever the position changed.
     *
     * <p>Without this the green rings keep describing the position that was on the board when the
     * piece was picked up: the opponent moving would leave stale, no longer legal markers behind.</p>
     */
    private void syncSelection() {
        MatchSnapshot snapshot = session.snapshot();
        int revision = snapshot == null ? -1 : snapshot.revision();
        if (revision == lastRevision) {
            return;
        }
        lastRevision = revision;
        if (selected < 0) {
            return;
        }
        List<Integer> refreshed = session.canMove() ? session.legalTargetsFrom(selected) : List.of();
        if (refreshed.isEmpty()) {
            clearSelection();
        } else {
            targets = new ArrayList<>(refreshed);
        }
    }

    private boolean flipped() {
        return Config.rotateBoardForBlack() && session.viewerSide() == Side.BLACK;
    }

    private boolean canResign() {
        MatchSnapshot snapshot = session.snapshot();
        return snapshot != null && snapshot.phase() == MatchPhase.PLAYING && session.isSeated();
    }

    /**
     * Clearing the board is offered to the two players, in any phase: before a game it throws away a
     * suspended position, during one it is a rematch, and after one it starts the next game without
     * anybody having to sit down again. An onlooker is not given the button, and the server refuses
     * the request anyway.
     */
    private boolean canReset() {
        return session.snapshot() != null && session.isSeated();
    }

    private int buttonAreaLeft() {
        int right = width - BUTTON_RIGHT_INSET;
        int used = 0;
        if (canResign()) {
            used += BUTTON_WIDTH + BUTTON_GAP;
        }
        if (canReset()) {
            used += BUTTON_WIDTH + BUTTON_GAP;
        }
        return right - used;
    }

    // ---------------------------------------------------------------- rendering

    /**
     * Deliberately empty, but load-bearing.
     *
     * <p>{@code Screen#render} calls this itself, <em>after</em> the subclass has drawn. Running the
     * vanilla implementation there blurs and darkens whatever is already on the screen - which is the
     * board - so the board would come out out of focus while the vanilla buttons drawn afterwards
     * stayed sharp. The background is therefore requested explicitly, once, at the top of
     * {@link #render}, where the blur belongs: behind the board.</p>
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty; see the class comment above.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The blurred world, drawn first so everything else sits on top of it. Calling it here rather
        // than letting Screen#render do it later is the whole trick: a blur that runs after the board
        // is drawn is the bug this screen used to have.
        if (minecraft != null && minecraft.level == null) {
            // No world to blur (the level unloaded while the screen was open): fall back to vanilla.
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        } else {
            // Blur only, without the vanilla menu veil. The veil darkens the world far more than a
            // translucent board needs; taking just the blur leaves the tint to drawScrim, where it can
            // be tuned to keep the world recognisable and the text readable at the same time.
            super.renderBlurredBackground(partialTick);
        }

        ensureLayout();
        syncWidgets();
        syncSelection();
        drawScrim(graphics);

        MatchSnapshot snapshot = session.snapshot();
        if (snapshot == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        drawPanel(graphics);
        drawGrid(graphics);
        drawLastMove(graphics, snapshot);
        drawPieces(graphics);
        drawOverlays(graphics, snapshot);
        drawTopBar(graphics, snapshot);
        drawBanner(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Tints the blurred world just enough for text and the board to read against it.
     *
     * <p>Kept translucent on purpose: the point of blurring the world is that you can still tell where
     * you are. The board itself stays opaque, because a see-through playing surface costs exactly the
     * contrast the pieces need.</p>
     */
    private void drawScrim(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, SCRIM_FILL);
        graphics.fill(0, BoardGuiLayout.TOP_BAR, width, BoardGuiLayout.TOP_BAR + 6, SCRIM_BOTTOM);
        graphics.fill(0, height - 18, width, height, SCRIM_BOTTOM);
    }

    /**
     * Rebuilds the geometry if the drawing area changed without {@link #init()} being called again.
     *
     * <p>Vanilla does re-initialise screens on a resize, but a screen whose layout is one frame stale
     * is drawn off-centre and clipped, and the cost of guarding against it is a comparison.</p>
     */
    private void ensureLayout() {
        boolean flip = flipped();
        if (layout == null || !layout.matches(width, height) || flip != layoutFlipped) {
            layout = BoardGuiLayout.create(width, height, flip);
            layoutFlipped = flip;
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        int left = layout.panelX();
        int top = layout.panelY();
        int right = layout.panelRight();
        int bottom = layout.panelBottom();
        // A soft shadow, widened outwards, so the board separates from the blurred world behind it
        // instead of looking pasted onto it.
        for (int ring = 6; ring >= 1; ring--) {
            graphics.fill(left - ring - 2, top - ring - 2, right + ring + 2, bottom + ring + 2, PANEL_SHADOW);
        }
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, PANEL_FRAME);
        graphics.fill(left, top, right, bottom, PANEL_FACE);
        for (int y = top + 4; y < bottom - 2; y += 5) {
            graphics.fill(left + 2, y, right - 2, y + 1, PANEL_GRAIN);
        }
    }

    private void drawGrid(GuiGraphics graphics) {
        int left = layout.gridLeft();
        int right = layout.gridRight();
        int top = layout.gridTop();
        int bottom = layout.gridBottom();

        for (int rank = 0; rank < Squares.RANKS; rank++) {
            int y = layout.y(rank);
            graphics.fill(left, y, right + 1, y + 1, GRID_COLOUR);
        }
        for (int file = 0; file < Squares.FILES; file++) {
            int x = layout.x(file);
            if (file == 0 || file == Squares.FILES - 1) {
                graphics.fill(x, top, x + 1, bottom + 1, GRID_COLOUR);
            } else {
                // The river interrupts the inner files.
                graphics.fill(x, layout.y(4), x + 1, bottom + 1, GRID_COLOUR);
                graphics.fill(x, top, x + 1, layout.y(5) + 1, GRID_COLOUR);
            }
        }

        drawDiagonal(graphics, 3, 0, 5, 2);
        drawDiagonal(graphics, 5, 0, 3, 2);
        drawDiagonal(graphics, 3, 7, 5, 9);
        drawDiagonal(graphics, 5, 7, 3, 9);

        int size = Math.max(8, Math.round(layout.cell() * 1.02F));
        int riverCentre = (layout.y(4) + layout.y(5)) / 2;
        int gridLeft = layout.gridLeft();
        int cell = layout.cell();
        double[] riverPositions = layout.flipped() ? RIVER_FILE_POSITIONS_FLIPPED : RIVER_FILE_POSITIONS;
        for (int slot = 0; slot < riverPositions.length; slot++) {
            int x = gridLeft + (int) Math.round(riverPositions[slot] * cell) - size / 2;
            blitTile(graphics, BoardGlyphAtlas.riverTile(slot), x, riverCentre - size / 2, size);
        }
    }

    private void drawDiagonal(GuiGraphics graphics, int fileA, int rankA, int fileB, int rankB) {
        int x0 = layout.x(fileA);
        int y0 = layout.y(rankA);
        int x1 = layout.x(fileB);
        int y1 = layout.y(rankB);
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) {
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = x0 + Math.round((x1 - x0) * (float) step / steps);
            int y = y0 + Math.round((y1 - y0) * (float) step / steps);
            graphics.fill(x, y, x + 1, y + 1, GRID_COLOUR);
        }
    }

    /** Fills drawn underneath the pieces: where the last ply came from, and where it landed. */
    private void drawLastMove(GuiGraphics graphics, MatchSnapshot snapshot) {
        if (Config.showLastMove() && snapshot.hasLastMove()) {
            fill(graphics, snapshot.lastFrom(), LAST_MOVE_FILL, 0.42D);
            fill(graphics, snapshot.lastTo(), LAST_MOVE_FILL, 0.42D);
        }
    }

    /** Rings drawn on top of the pieces, so an occupied destination is still visible. */
    private void drawOverlays(GuiGraphics graphics, MatchSnapshot snapshot) {
        if (snapshot.checkedSide() != null) {
            for (int square = 0; square < Squares.COUNT; square++) {
                Piece piece = session.board().pieceAt(square);
                if (piece != null && piece.type() == PieceType.GENERAL
                        && piece.side() == snapshot.checkedSide()) {
                    halo(graphics, square);
                }
            }
        }
        if (Config.highlightLegalMoves() && canResign()) {
            for (int target : targets) {
                ring(graphics, target, TARGET_RING, 0.38D);
            }
        }
        if (selected >= 0) {
            brackets(graphics, selected, SELECT_RING, 0.5D);
        }
    }

    private void drawPieces(GuiGraphics graphics) {
        int size = Math.max(8, Math.round(layout.cell() * 0.96F));
        for (int square = 0; square < Squares.COUNT; square++) {
            Piece piece = session.board().pieceAt(square);
            if (piece == null) {
                continue;
            }
            blitTile(graphics, BoardGlyphAtlas.pieceTile(piece.type(), piece.side()),
                    layout.xOf(square) - size / 2, layout.yOf(square) - size / 2, size);
        }
    }

    private void blitTile(GuiGraphics graphics, int tile, int x, int y, int size) {
        graphics.blit(BoardGlyphAtlas.TEXTURE, x, y, size, size,
                BoardGlyphAtlas.tileU(tile), BoardGlyphAtlas.tileV(tile),
                BoardGlyphAtlas.TILE, BoardGlyphAtlas.TILE,
                BoardGlyphAtlas.SHEET_WIDTH, BoardGlyphAtlas.SHEET_HEIGHT);
    }

    /**
     * The marker for a general that is in check: a thin ring hugging the piece itself.
     *
     * <p>One blit of {@link BoardSprites#HALO}. The ring used to be a run of small filled squares on a
     * circle, which is scalloped by construction, has a line width quantised to whole pixels and costs
     * dozens of draws; the sprite is anti-aliased once at generation time and scales cleanly to any
     * board size.</p>
     */
    private void halo(GuiGraphics graphics, int square) {
        int size = Math.max(6, Math.round(layout.cell() * BoardSprites.HALO_SPAN));
        int x = layout.xOf(square) - size / 2;
        int y = layout.yOf(square) - size / 2;
        graphics.blit(BoardSprites.HALO, x, y, size, size,
                0.0F, 0.0F, BoardSprites.HALO_PIXELS, BoardSprites.HALO_PIXELS,
                BoardSprites.HALO_PIXELS, BoardSprites.HALO_PIXELS);
    }

    private void fill(GuiGraphics graphics, int square, int colour, double halfCells) {
        int half = (int) Math.max(3, Math.round(layout.cell() * halfCells));
        int x = layout.xOf(square);
        int y = layout.yOf(square);
        graphics.fill(x - half, y - half, x + half, y + half, colour);
    }

    private void ring(GuiGraphics graphics, int square, int colour, double halfCells) {
        int half = (int) Math.max(3, Math.round(layout.cell() * halfCells));
        int thickness = Math.max(2, layout.cell() / 10);
        int x = layout.xOf(square);
        int y = layout.yOf(square);
        graphics.fill(x - half, y - half, x + half, y - half + thickness, colour);
        graphics.fill(x - half, y + half - thickness, x + half, y + half, colour);
        graphics.fill(x - half, y - half + thickness, x - half + thickness, y + half - thickness, colour);
        graphics.fill(x + half - thickness, y - half + thickness, x + half, y + half - thickness, colour);
    }

    /**
     * The marker for the piece the player picked up: four corner brackets rather than a closed ring.
     *
     * <p>A closed square fights with the printed grid, which is also made of squares, so the eye has to
     * work out which square means "selected". Four corners point at the piece instead of enclosing it,
     * and they leave the edges of the cell - where the grid lines run - untouched.</p>
     */
    private void brackets(GuiGraphics graphics, int square, int colour, double halfCells) {
        int half = (int) Math.max(4, Math.round(layout.cell() * halfCells));
        int thickness = Math.max(2, layout.cell() / 10);
        int arm = Math.max(thickness + 3, Math.round(half * 0.55F));
        int x = layout.xOf(square);
        int y = layout.yOf(square);
        int left = x - half;
        int right = x + half;
        int top = y - half;
        int bottom = y + half;
        graphics.fill(left, top, left + arm, top + thickness, colour);
        graphics.fill(left, top, left + thickness, top + arm, colour);
        graphics.fill(right - arm, top, right, top + thickness, colour);
        graphics.fill(right - thickness, top, right, top + arm, colour);
        graphics.fill(left, bottom - thickness, left + arm, bottom, colour);
        graphics.fill(left, bottom - arm, left + thickness, bottom, colour);
        graphics.fill(right - arm, bottom - thickness, right, bottom, colour);
        graphics.fill(right - thickness, bottom - arm, right, bottom, colour);
    }

    // ---------------------------------------------------------------- information strip

    private void drawTopBar(GuiGraphics graphics, MatchSnapshot snapshot) {
        graphics.fill(0, 0, width, BoardGuiLayout.TOP_BAR, BAR_FILL);
        graphics.fill(0, BoardGuiLayout.TOP_BAR - 1, width, BoardGuiLayout.TOP_BAR, BAR_LINE);

        // Whole-number scale only, and 2x at that. Minecraft renders CJK from a 16 pixel bitmap into
        // a 9 pixel cell, so at 1x every stroke is resampled and the whole line looks out of focus;
        // at 2x the glyph is drawn near its native size and reads cleanly. This is the single biggest
        // legibility difference in the whole screen.
        drawScaled(graphics, title, 10, BoardGuiLayout.FIRST_ROW, 2.0F, TITLE_COLOUR);
        int cursor = 10 + font.width(title) * 2 + 18;

        Component status = statusText(snapshot);
        int statusColour = session.isSeated() && snapshot.checkedSide() == session.viewerSide()
                ? WARN_COLOUR
                : STATUS_COLOUR;
        drawScaled(graphics, status, cursor, BoardGuiLayout.FIRST_ROW, 2.0F, statusColour);

        Component info = infoText(snapshot);
        int available = buttonAreaLeft() - 10 - 12;
        if (available > 48) {
            if (font.width(info) * 2 > available) {
                info = Translations.message("gui.move_count", snapshot.plyCount());
            }
            if (font.width(info) * 2 <= available) {
                drawScaled(graphics, info, 10, BoardGuiLayout.SECOND_ROW, 2.0F, LABEL_COLOUR);
            }
        }
    }

    private void drawScaled(GuiGraphics graphics, Component text, int x, int y, float scale, int colour) {
        if (scale == 1.0F) {
            graphics.drawString(font, text, x, y, colour, true);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, colour, true);
        graphics.pose().popPose();
    }

    private Component statusText(MatchSnapshot snapshot) {
        if (snapshot.phase() == MatchPhase.WAITING) {
            return Translations.message("gui.waiting");
        }
        if (snapshot.status().isFinished()) {
            return Translations.outcome(new GameOutcome(snapshot.status(), snapshot.reason()));
        }
        if (snapshot.checkedSide() != null && snapshot.checkedSide() == session.viewerSide()) {
            return Translations.message("gui.you_are_checked");
        }
        if (!session.isSeated()) {
            return Translations.message("gui.spectating", Translations.side(snapshot.sideToMove()));
        }
        if (snapshot.sideToMove() == session.viewerSide()) {
            return Translations.message("gui.your_turn");
        }
        return Translations.message("gui.opponent_turn", Translations.side(snapshot.sideToMove()));
    }

    private Component infoText(MatchSnapshot snapshot) {
        if (snapshot.phase() == MatchPhase.WAITING) {
            return Translations.message("gui.waiting_hint");
        }
        Side mine = session.viewerSide();
        Component seat = mine == null
                ? Translations.message("gui.seat.spectator")
                : Translations.message("gui.seat.playing", Translations.side(mine));
        if (snapshot.noCaptureLimit() > 0) {
            return Component.empty()
                    .append(seat)
                    .append(Translations.message("gui.info_separator"))
                    .append(Translations.message("gui.move_count", snapshot.plyCount()))
                    .append(Translations.message("gui.info_separator"))
                    .append(Translations.message("gui.quiet_moves",
                            snapshot.pliesSinceCapture(), snapshot.noCaptureLimit()));
        }
        return Component.empty()
                .append(seat)
                .append(Translations.message("gui.info_separator"))
                .append(Translations.message("gui.move_count", snapshot.plyCount()));
    }

    private void drawBanner(GuiGraphics graphics) {
        if (banner == null) {
            return;
        }
        int textWidth = font.width(banner) * 2;
        int halfWidth = textWidth / 2 + 20;
        int centreX = width / 2;
        int centreY = height / 2;
        graphics.fill(centreX - halfWidth, centreY - 20, centreX + halfWidth, centreY + 22, BANNER_FILL);
        graphics.fill(centreX - halfWidth, centreY - 20, centreX + halfWidth, centreY - 19, BANNER_EDGE);
        graphics.fill(centreX - halfWidth, centreY + 21, centreX + halfWidth, centreY + 22, BANNER_EDGE);
        drawScaled(graphics, banner, centreX - textWidth / 2, centreY - 8, 2.0F, bannerColour);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        int square = layout.squareAt(mouseX, mouseY);
        if (square < 0) {
            clearSelection();
            return true;
        }
        if (selected >= 0 && targets.contains(square)) {
            session.requestMove(selected, square);
            clearSelection();
            return true;
        }
        if (session.canMove()) {
            List<Integer> destinations = session.legalTargetsFrom(square);
            if (!destinations.isEmpty()) {
                selected = square;
                targets = new ArrayList<>(destinations);
                return true;
            }
            if (session.isHeldBackByCheck(square)) {
                // The piece has moves, but every one of them would expose its own general. Saying so is
                // the difference between a rule and what looks like a broken piece.
                hint("gui.pinned");
                return true;
            }
        }
        clearSelection();
        return true;
    }

    /** A short line in the action bar, where the player is already looking while they play. */
    private void hint(String key) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Translations.message(key), true);
        }
    }

    private void clearSelection() {
        selected = -1;
        targets = List.of();
    }

    @Override
    public boolean isPauseScreen() {
        // The world keeps running while the board is open, exactly like a game played at a table.
        return false;
    }

    // ---------------------------------------------------------------- server messages

    /** Called when the server announces the verdict. */
    public void showResult(MatchFinishedPayload payload) {
        this.banner = Translations.outcome(new GameOutcome(payload.status(), payload.reason()));
        this.bannerColour = payload.status() == GameStatus.DRAW ? 0xFFE0E0E0 : TITLE_COLOUR;
        clearSelection();
    }

    /** Called when the server closes the board itself, for example because it was destroyed. */
    public void closeFromServer(String messageKey) {
        leaveRequested = true;
        if (minecraft != null) {
            minecraft.setScreen(null);
            if (minecraft.player != null && messageKey != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable(messageKey).withStyle(ChatFormatting.YELLOW), false);
            }
        }
    }

    /**
     * The resign button: asks the server to concede, and then does nothing else.
     *
     * <p>The board deliberately stays open. Conceding ends the game, and the player who conceded is
     * entitled to see the verdict for the same few seconds as the winner; closing the screen here would
     * make the result flash past for one side only. The verdict arrives as a finished payload and the
     * server closes the board for both players after the usual delay.</p>
     */
    private void resign() {
        BlockPos origin = session.origin();
        if (origin != null) {
            session.requestLeave(origin, true);
        }
    }

    /**
     * The single exit hook, reached only through escape.
     *
     * <p>Escape closes the screen through the vanilla {@code onClose}, which lands here; the server is
     * told exactly once and the local session is dropped, so a player who closes the board can never
     * leave a ghost seat behind.</p>
     */
    @Override
    public void removed() {
        super.removed();
        BlockPos origin = session.origin();
        if (!leaveRequested && origin != null) {
            leaveRequested = true;
            session.requestLeave(origin, false);
        }
        session.close();
    }
}
