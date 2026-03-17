package ua.grigo.frontiercontracts.board;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Sign;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.util.MessageService;
import ua.grigo.frontiercontracts.util.TextUtil;

public final class BoardSignService {
    private static final int SIGN_LINE_COUNT = 4;
    private static final int SIGN_LINE_LENGTH = 15;
    private static final String HEADER_TEXT = "contact";

    private final Server server;
    private final MessageService messages;

    public BoardSignService(Server server, MessageService messages) {
        this.server = server;
        this.messages = messages;
    }

    public void syncBoard(Board board, List<ContractOffer> localOffers) {
        BoardLayout layout = BoardLayout.fromBoard(board, server);
        if (layout == null) {
            return;
        }

        writeSign(layout.headerSign(), List.of(HEADER_TEXT, "", "", ""));

        List<Location> taskSigns = layout.taskSignBlocks();
        if (!board.active() || !board.isValidStructure()) {
            List<String> invalidLines = normalizeLines(messages.rawList("signs.invalid"));
            for (Location location : taskSigns) {
                writeSign(location, invalidLines);
            }
            return;
        }

        List<ContractOffer> offers = localOffers == null
            ? List.of()
            : localOffers.stream()
                .filter(ContractOffer::active)
                .filter(ContractOffer::isBoardLocal)
                .sorted(Comparator.comparingLong(ContractOffer::createdAtEpochSeconds))
                .limit(BoardLayout.TASK_SIGN_COUNT)
                .toList();

        List<String> idleLines = normalizeLines(messages.rawList("signs.idle"));
        for (int index = 0; index < taskSigns.size(); index++) {
            List<String> lines = index < offers.size()
                ? buildOfferLines(offers.get(index))
                : idleLines;
            writeSign(taskSigns.get(index), lines);
        }
    }

    private List<String> buildOfferLines(ContractOffer offer) {
        List<String> lines = new ArrayList<>(SIGN_LINE_COUNT);
        List<String> nameLines = wrapWords(TextUtil.prettyToken(offer.requiredMaterial().name()), 2, SIGN_LINE_LENGTH);
        lines.add(nameLines.isEmpty() ? "" : nameLines.getFirst());
        lines.add(nameLines.size() > 1 ? nameLines.get(1) : "");
        lines.add(trim(offer.deliveredAmount() + "/" + offer.requiredAmount()));
        lines.add(trim(defaultIfMissing(messages.raw("signs.labels.deliver"), "Deliver")));
        return normalizeLines(lines);
    }

    private void writeSign(Location location, List<String> lines) {
        if (!(location.getBlock().getState() instanceof Sign sign)) {
            return;
        }

        List<String> normalized = normalizeLines(lines);
        if (tryWriteFront(sign, normalized)) {
            sign.update(true, false);
            return;
        }

        for (int index = 0; index < SIGN_LINE_COUNT; index++) {
            sign.setLine(index, normalized.get(index));
        }
        sign.update(true, false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean tryWriteFront(Sign sign, List<String> lines) {
        try {
            Class<?> sideEnumClass = Class.forName("org.bukkit.block.sign.Side");
            Object front = Enum.valueOf((Class<? extends Enum>) sideEnumClass.asSubclass(Enum.class), "FRONT");
            Object signSide = sign.getClass().getMethod("getSide", sideEnumClass).invoke(sign, front);
            Method setLine = signSide.getClass().getMethod("setLine", int.class, String.class);
            for (int index = 0; index < SIGN_LINE_COUNT; index++) {
                setLine.invoke(signSide, index, lines.get(index));
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private List<String> normalizeLines(List<String> input) {
        List<String> lines = new ArrayList<>(SIGN_LINE_COUNT);
        for (int index = 0; index < SIGN_LINE_COUNT; index++) {
            String line = index < input.size() ? input.get(index) : "";
            lines.add(trim(line));
        }
        return lines;
    }

    private List<String> wrapWords(String value, int maxLines, int maxLength) {
        List<String> lines = new ArrayList<>(maxLines);
        String[] words = value == null ? new String[0] : value.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (candidate.length() <= maxLength) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(trim(current.toString()));
                current.setLength(0);
            }
            current.append(word);
            if (lines.size() == maxLines - 1) {
                break;
            }
        }
        if (!current.isEmpty() && lines.size() < maxLines) {
            lines.add(trim(current.toString()));
        }
        while (lines.size() < maxLines) {
            lines.add("");
        }
        return lines;
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip();
        return normalized.length() <= SIGN_LINE_LENGTH ? normalized : normalized.substring(0, SIGN_LINE_LENGTH);
    }

    private String defaultIfMissing(String value, String fallback) {
        return value == null || value.isBlank() || value.startsWith("signs.") ? fallback : value;
    }
}
