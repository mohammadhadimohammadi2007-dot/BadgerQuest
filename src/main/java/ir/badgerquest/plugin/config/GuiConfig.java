package ir.badgerquest.plugin.config;

import java.util.List;
import java.util.Map;

public record GuiConfig(String title,
                        String titleAccepted,
                        int size,
                        List<Integer> inputSlots,
                        List<Integer> displaySlots,
                        List<Integer> acceptSlots,
                        List<Integer> questInfoSlots,
                        List<Integer> streakSlots,
                        List<Integer> infoSlots,
                        List<Integer> closeSlots,
                        List<Integer> emptySlots,
                        Map<String, GuiItem> items) {

    public GuiItem item(String key) {
        return items.getOrDefault(key, GuiItem.EMPTY);
    }
}
