package ua.grigo.frontiercontracts.board;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import ua.grigo.frontiercontracts.model.Board;
import ua.grigo.frontiercontracts.model.ContractOffer;
import ua.grigo.frontiercontracts.model.ContractType;
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
        syncBoard(board, localOffers, "{done}/{total} req");
    }

    public void syncBoard(Board board, List<ContractOffer> localOffers, String constructionProgressFormat) {
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
                .limit(BoardLayout.TASK_SIGN_COUNT)
                .toList();

        List<String> idleLines = normalizeLines(messages.rawList("signs.idle"));
        for (int index = 0; index < taskSigns.size(); index++) {
            List<String> lines = index < offers.size()
                ? buildOfferLines(offers.get(index), constructionProgressFormat)
                : idleLines;
            writeSign(taskSigns.get(index), lines);
        }
    }

    private List<String> buildOfferLines(ContractOffer offer, String constructionProgressFormat) {
        return offer.type() == ContractType.CONSTRUCTION
            ? buildConstructionLines(offer, constructionProgressFormat)
            : buildDeliveryLines(offer);
    }

    private List<String> buildDeliveryLines(ContractOffer offer) {
        List<String> lines = new ArrayList<>(SIGN_LINE_COUNT);
        List<String> nameLines = wrapWords(TextUtil.prettyToken(offer.requiredMaterial().name()), 2, SIGN_LINE_LENGTH);
        lines.add(nameLines.isEmpty() ? "" : nameLines.getFirst());
        lines.add(nameLines.size() > 1 ? nameLines.get(1) : "");
        lines.add(trim(offer.deliveredAmount() + "/" + offer.requiredAmount()));
        lines.add(trim(defaultIfMissing(messages.raw("signs.labels.deliver"), "Deliver")));
        return normalizeLines(lines);
    }

    private List<String> buildConstructionLines(ContractOffer offer, String progressFormat) {
        List<String> lines = new ArrayList<>(SIGN_LINE_COUNT);
        List<String> nameLines = wrapWords(TextUtil.normalizePlain(offer.title()).isBlank() ? "Build" : stripColor(offer.title()), 2, SIGN_LINE_LENGTH);
        lines.add(nameLines.isEmpty() ? "" : nameLines.getFirst());
        lines.add(nameLines.size() > 1 ? nameLines.get(1) : "");
        String progress = progressFormat
            .replace("{done}", Integer.toString(offer.completedRequirementCount()))
            .replace("{total}", Integer.toString(offer.totalRequirementCount()));
        lines.add(trim(progress));
        lines.add(trim(defaultIfMissing(messages.raw("signs.labels.build"), "Build")));
        return normalizeLines(lines);
    }

    private void writeSign(Location location, List<String> lines) {
        if (!(location.getBlock().getState() instanceof Sign sign)) {
            return;
        }

        List<String> normalized = normalizeLines(lines);
        for (int index = 0; index < SIGN_LINE_COUNT; index++) {
            sign.getSide(Side.FRONT).setLine(index, normalized.get(index));
        }
        sign.update(true, false);
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

    private String stripColor(String raw) {
        return TextUtil.colorize(raw).replaceAll("(?i)\u00a7[0-9a-fk-or]", "");
    }
}
