package io.github.zgxhzhr.meowmachine.review;

import java.util.ArrayList;
import java.util.List;

/** 修改前后的简单行级对比（给审核员看，也用来判断「越改越倒退」）。 */
public final class EditDiffs {

    public record Diff(int added, int removed, int oldChars, int newChars, List<String> removedSample) {
        /**
         * 改动是否小到「实质等于没改」。
         *
         * <p>用于已通过条目的再编辑：原稿本来合规，只加一个字 AI 也会判通过，
         * 但这类编辑没有审核价值。判定看字数变化与变动行数，取绝对值和比例两条线。
         */
        public boolean trivial() {
            int delta = Math.abs(newChars - oldChars);
            // 改动一行会同时记一次 added 和一次 removed，这里按「变了多少行」算
            int changedLines = Math.max(added, removed);
            if (oldChars >= 400) {
                return delta <= Math.max(8, oldChars / 50) && changedLines <= 2;
            }
            return delta <= 4 && changedLines <= 1;
        }

        public boolean regressed() {
            // 删得比加得多、或总字数缩水三成以上：视为倒退
            return removed > added || (oldChars > 120 && newChars < oldChars * 0.7);
        }

        public List<String> summaryLines() {
            List<String> out = new ArrayList<>();
            out.add("字数：" + oldChars + " → " + newChars + "（新增 " + added + " 行 / 删除 " + removed + " 行）");
            for (String line : removedSample) {
                out.add("删除： " + line);
            }
            return out;
        }
    }

    private EditDiffs() {
    }

    public static Diff of(String oldBody, String newBody) {
        List<String> oldLines = lines(oldBody);
        List<String> newLines = lines(newBody);
        int added = 0;
        int removed = 0;
        List<String> removedSample = new ArrayList<>();
        for (String line : newLines) {
            if (!oldLines.contains(line)) {
                added++;
            }
        }
        for (String line : oldLines) {
            if (!newLines.contains(line)) {
                removed++;
                if (removedSample.size() < 3 && line.length() > 4) {
                    removedSample.add(line.length() > 24 ? line.substring(0, 24) + "…" : line);
                }
            }
        }
        return new Diff(added, removed,
                strip(oldBody).length(), strip(newBody).length(), removedSample);
    }

    private static List<String> lines(String body) {
        List<String> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        // 编辑器输出的 HTML 常常是单行（toHtml 不产生换行），先按标签换成换行再比，
        // 否则行级对比恒为 0/0，「删得比加得多」这条判据等于死代码（审查 P1）
        String text = io.github.zgxhzhr.meowmachine.richtext.RichHtml.stripTags(body);
        for (String raw : text.split("\n")) {
            String t = strip(raw).trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "").replaceAll("\\s", "");
    }
}
