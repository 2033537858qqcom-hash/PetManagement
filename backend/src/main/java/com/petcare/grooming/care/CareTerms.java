package com.petcare.grooming.care;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Fixed vocabulary for pet records, grooming scores and the risk text shown before a wash. */
public final class CareTerms {
    private CareTerms() {}

    public static final Map<String, String> AGES = map(
        "PUPPY", "幼年（1岁内）",
        "YOUNG", "青年（1-3岁）",
        "ADULT", "成年（3-7岁）",
        "SENIOR", "老年（7岁以上）");
    public static final Map<String, String> ALLERGIES = map(
        "NONE", "无已知过敏",
        "FRAGRANCE", "香波香精过敏",
        "PROTEIN", "蛋白质过敏",
        "POLLEN", "花粉过敏",
        "FLEA", "跳蚤叮咬过敏");
    public static final Map<String, String> SKIN_HISTORY = map(
        "HEALTHY", "皮肤健康",
        "DANDER", "轻度皮屑",
        "PODODERMATITIS", "趾间炎史",
        "EAR_MITE", "耳螨史",
        "FUNGAL", "真菌感染史",
        "DERMATITIS", "过敏性皮炎史");
    public static final Map<String, String> HISTORIES = map(
        "NONE", "无既往洗护",
        "BASIC", "定期基础洗护",
        "STYLING", "曾做造型修剪",
        "SPA", "曾做深层SPA",
        "MATTING", "曾处理严重打结");
    public static final Map<String, Integer> SKIN_SCORE = Map.of(
        "CLEAR", 100, "DRY", 80, "FLAKY", 60, "IRRITATED", 40);
    public static final Map<String, String> SKIN_LABEL = map(
        "CLEAR", "健康无红疹", "DRY", "轻度干燥", "FLAKY", "明显皮屑", "IRRITATED", "红疹或破损");
    public static final Map<String, Integer> COAT_SCORE = Map.of(
        "SMOOTH", 100, "LIGHT", 80, "MODERATE", 60, "MATTED", 40);
    public static final Map<String, String> COAT_LABEL = map(
        "SMOOTH", "柔顺无打结", "LIGHT", "轻微打结", "MODERATE", "中度打结", "MATTED", "严重毡化");
    public static final Map<String, Integer> EAR_SCORE = Map.of(
        "CLEAN", 100, "MILD", 75, "ODOR", 50, "MITE", 30);
    public static final Map<String, String> EAR_LABEL = map(
        "CLEAN", "清洁无异味", "MILD", "轻度分泌", "ODOR", "异味或红肿", "MITE", "疑似耳螨");
    public static final List<String> DOG_BREEDS = List.of("比熊", "金毛", "柯基", "泰迪", "萨摩耶", "博美", "哈士奇", "拉布拉多", "柴犬", "边牧", "法斗", "雪纳瑞", "马尔济斯", "约克夏", "德牧", "阿拉斯加", "西高地", "喜乐蒂", "松狮", "贵宾", "中华田园犬", "其他犬种");
    public static final List<String> CAT_BREEDS = List.of("布偶猫", "英短", "美短", "橘猫", "暹罗猫", "缅因猫", "加菲猫", "金渐层", "银渐层", "蓝猫", "无毛猫", "德文卷毛猫", "俄罗斯蓝猫", "波斯猫", "孟买猫", "森林猫", "中华田园猫", "其他猫种");
    public static final List<String> COATS = List.of("短毛", "卷毛", "长毛", "双层毛");
    public static final List<String> SIZES = List.of("SMALL", "MEDIUM", "LARGE", "GIANT");
    public static final List<String> CATEGORIES = List.of("基础洗护", "造型美容", "深层护理", "猫咪专护");
    public static final List<Integer> DURATIONS = List.of(45, 60, 75, 90, 120);
    public static final List<BigDecimal> WEIGHTS = List.of(2, 3, 4, 5, 6, 8, 10, 12, 15, 18, 22, 26, 28, 32, 36).stream().map(v -> new BigDecimal(v + ".0")).toList();
    private static final Pattern PET_NAME = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9]{1,12}$");
    private static final Pattern SERVICE_NAME = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9]{2,20}$");

    public static int score(String skin, String coat, String ear) {
        Integer skinScore = SKIN_SCORE.get(skin);
        Integer coatScore = COAT_SCORE.get(coat);
        Integer earScore = EAR_SCORE.get(ear);
        if (skinScore == null || coatScore == null || earScore == null) {
            throw new IllegalArgumentException("请从固定词条中选择皮肤、毛发和耳道状态");
        }
        return (int) Math.round(skinScore * 0.4 + coatScore * 0.35 + earScore * 0.25);
    }

    private static final Pattern FREE_TEXT = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9()（）\\-·\\s]{1,30}$");

    public static String petError(String name, String species, String breed, String ageBand, String sizeType, BigDecimal weight, String coat, String allergy, String skinHistory, String groomingHistory, BigDecimal currentWeight) {
        if (name == null || !PET_NAME.matcher(name.trim()).matches()) return "宠物昵称请使用 1 到 12 位中文、字母或数字";
        if (!label(species, 20)) return "请选择或填写宠物种类";
        if (!label(breed, 30)) return "请选择或填写品种";
        if (!AGES.containsKey(ageBand) && !label(ageBand, 20)) return "请选择或填写年龄";
        if (!SIZES.contains(sizeType)) return "请选择体型";
        if (!allowedWeight(weight) && (currentWeight == null || weight == null || currentWeight.compareTo(weight) != 0)) return "请输入 0.1 到 99.99 千克之间的有效体重";
        if (!COATS.contains(coat) && !label(coat, 20)) return "请选择或填写毛发";
        if (!ALLERGIES.containsKey(allergy) && !label(allergy, 30)) return "请选择或填写过敏史";
        if (!SKIN_HISTORY.containsKey(skinHistory) && !label(skinHistory, 30)) return "请选择或填写皮肤病史";
        if (!HISTORIES.containsKey(groomingHistory) && !label(groomingHistory, 30)) return "请选择或填写既往洗护记录";
        return null;
    }

    private static boolean label(String value, int max) {
        return value != null && FREE_TEXT.matcher(value.trim()).matches() && value.trim().length() <= max;
    }

    public static String serviceError(String name, String category, BigDecimal price, Integer duration, String species) {
        if (name == null || !SERVICE_NAME.matcher(name.trim()).matches()) return "项目名称请使用 2 到 20 位中文、字母或数字";
        if (!CATEGORIES.contains(category) && !label(category, 20)) return "请选择或填写服务分类";
        if (price == null || price.scale() > 2 || price.compareTo(BigDecimal.ONE) < 0 || price.compareTo(new BigDecimal("2000")) > 0) return "价格需在 1 到 2000 元之间";
        if (duration == null || duration < 15 || duration > 705 || duration % 5 != 0) return "护理时长请填写 15 到 705 分钟，且为 5 的倍数";
        if (!validServiceSpecies(species)) return "请选择或填写适用宠物";
        return null;
    }
    public static boolean validServiceSpecies(String species) {
        if (species == null) return false;
        String value = species.trim();
        if (value.equals("ALL") || value.equals("DOG") || value.equals("CAT") || value.equals("兔子") || value.equals("鸟类") || value.equals("仓鼠")) return true;
        return label(value, 20);
    }

    public static boolean allowedWeight(BigDecimal weight) {
        return weight != null && weight.scale() <= 2 && weight.compareTo(new BigDecimal("0.1")) >= 0 && weight.compareTo(new BigDecimal("99.99")) <= 0;
    }

    public static List<String> risks(String age, String allergy, String skin, String history) {
        List<String> risks = new ArrayList<>();
        if (allergy != null && !"NONE".equals(allergy) && !allergy.isBlank()) {
            risks.add("洗护禁忌：" + ALLERGIES.getOrDefault(allergy, allergy) + "。请避开对应成分，优先使用低敏洗护。");
        }
        if (skin != null && !"HEALTHY".equals(skin) && !skin.isBlank()) {
            risks.add("皮肤病史：" + SKIN_HISTORY.getOrDefault(skin, skin) + "。洗护前检查患处，避免刺激和长时间吹风。");
        }
        if ("SENIOR".equals(age) || (age != null && age.contains("老"))) risks.add("老年宠物。请控制水温与吹风时长，并留意应激反应。");
        if ("MATTING".equals(history) || (history != null && history.contains("打结"))) risks.add("既往有严重打结。洗护前先确认毛毡范围，避免强行拉扯。");
        return risks;
    }

    public static void decorate(Map<String, Object> pet) {
        String age = text(pet.get("age_band"));
        String allergy = text(pet.get("allergy"));
        String skin = text(pet.get("skin_history"));
        String history = text(pet.get("grooming_history"));
        pet.put("age_label", AGES.getOrDefault(age, age));
        pet.put("allergy_label", ALLERGIES.getOrDefault(allergy, allergy));
        pet.put("skin_label", SKIN_HISTORY.getOrDefault(skin, skin));
        pet.put("grooming_label", HISTORIES.getOrDefault(history, history));
        pet.put("risks", risks(age, allergy, skin, history));
        pet.put("health_note", summary(age, allergy, skin, history));
    }

    public static String summary(String age, String allergy, String skin, String history) {
        List<String> parts = new ArrayList<>();
        if (age != null && !age.isBlank()) parts.add(AGES.getOrDefault(age, age));
        if (allergy != null && !allergy.isBlank()) parts.add(ALLERGIES.getOrDefault(allergy, allergy));
        if (skin != null && !skin.isBlank()) parts.add(SKIN_HISTORY.getOrDefault(skin, skin));
        if (history != null && !history.isBlank()) parts.add(HISTORIES.getOrDefault(history, history));
        return String.join(" · ", parts);
    }

    private static String text(Object value) { return value == null ? "" : value.toString(); }

    private static Map<String, String> map(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values;
    }
}
