package com.petcare.grooming.media;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class PhotoStorageService {
    public record Asset(String key, String label, String category, String description, String defaultUrl, String url, boolean customized) {}
    public record Image(byte[] bytes, String contentType, String version) {}
    private record Definition(String key, String label, String category, String description, String filename) {}
    private record Metadata(String contentType, String version) {}
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final List<Definition> DEFINITIONS = List.of(
        new Definition("entry-cover", "登录封面", "品牌封面", "登录与注册页面", "entry-cover.jpg"),
        new Definition("nuomi", "糯米", "宠物照片", "宠物档案、首页及护理卡片", "nuomi.png"),
        new Definition("wangcai", "旺财", "宠物照片", "宠物档案、护理卡片及侧栏", "wangcai.jpg"),
        new Definition("xueqiu", "雪球", "宠物照片", "宠物档案与护理卡片", "xueqiu.jpg"),
        new Definition("mianhua", "棉花糖", "宠物照片", "宠物档案与护理卡片", "mianhua.jpg"),
        new Definition("care-bath", "基础洗护", "洗护项目", "基础洗护项目与推荐卡片", "care-bath.jpg"),
        new Definition("care-styling", "造型修剪", "洗护项目", "修剪、造型项目与推荐卡片", "care-styling.jpg"),
        new Definition("care-spa", "舒缓 SPA", "洗护项目", "SPA 项目、推荐卡片与会员背景", "care-spa.jpg"),
        new Definition("care-cat", "猫咪洗护", "洗护项目", "猫咪护理项目、次卡与推荐卡片", "care-cat.jpg"),
        new Definition("product-shampoo", "低敏香波", "商品图片", "香波商品、商品订单与库存", "product-shampoo.jpg"),
        new Definition("product-bath", "碳酸泉泡腾锭", "商品图片", "泡腾锭商品、商品订单与库存", "product-bath.jpg"),
        new Definition("product-serum", "护毛精华", "商品图片", "护毛商品、商品订单与库存", "product-serum.jpg"),
        new Definition("care-member", "会员护理", "会员展示", "会员储值卡背景与洗护次卡", "care-member.jpg")
    );
    private final Path directory;
    private final Map<String, Metadata> stored = new HashMap<>();

    public PhotoStorageService(@Value("${pet-grooming.media-directory:./uploads/photos}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        for (var definition : DEFINITIONS) {
            Path path = path(definition);
            if (Files.isRegularFile(path)) {
                try {
                    if (Files.size(path) > MAX_BYTES) throw invalidImage();
                    byte[] bytes = Files.readAllBytes(path);
                    stored.put(definition.key(), new Metadata(validate(bytes), version(bytes)));
                } catch (IOException | ResponseStatusException exception) {
                    LoggerFactory.getLogger(PhotoStorageService.class).warn("Cannot load stored photo {}", definition.key());
                }
            }
        }
    }
    private Definition definition(String key) {
        return DEFINITIONS.stream().filter(item -> item.key().equals(key)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片位置不存在"));
    }
    private Path path(Definition definition) { return directory.resolve(definition.key() + ".image"); }
    private Asset describe(Definition definition) {
        var metadata = stored.get(definition.key());
        String defaultUrl = "/images/" + definition.filename();
        return new Asset(definition.key(), definition.label(), definition.category(), definition.description(), defaultUrl,
            metadata == null ? defaultUrl : "/api/media/photos/" + definition.key() + "/image?v=" + metadata.version(), metadata != null);
    }
    public synchronized List<Asset> catalogue() { return DEFINITIONS.stream().map(this::describe).toList(); }

    public synchronized Asset replace(String key, MultipartFile file) {
        var definition = definition(key);
        if (file.isEmpty()) throw invalidImage();
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 5 MB");
        Path temporary = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = validate(bytes);
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, definition.key() + "-", ".tmp");
            Files.write(temporary, bytes);
            try { Files.move(temporary, path(definition), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path(definition), StandardCopyOption.REPLACE_EXISTING); }
            stored.put(key, new Metadata(contentType, version(bytes)));
            return describe(definition);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图片暂时无法保存，请稍后重试");
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
    public synchronized Asset reset(String key) {
        var definition = definition(key);
        try { Files.deleteIfExists(path(definition)); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "暂时无法恢复图片，请稍后重试"); }
        stored.remove(key);
        return describe(definition);
    }
    public synchronized Image image(String key) {
        var definition = definition(key);
        var metadata = stored.get(key);
        if (metadata == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有自定义图片");
        try { return new Image(Files.readAllBytes(path(definition)), metadata.contentType(), metadata.version()); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片暂时无法读取"); }
    }
    public String saveProductImage(MultipartFile file) {
        if (file.isEmpty()) throw invalidImage();
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 5 MB");
        Path temporary = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = validate(bytes);
            String key = UUID.randomUUID() + (contentType.equals("image/png") ? ".png" : ".jpg");
            Path target = productPath(key);
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "product-", ".tmp");
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target); }
            return key;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图片暂时无法保存，请稍后重试");
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
    public Image productImage(String key) {
        Path path = productPath(key);
        try {
            if (Files.size(path) > MAX_BYTES) throw new IOException("Invalid image size");
            byte[] bytes = Files.readAllBytes(path);
            return new Image(bytes, key.endsWith(".png") ? "image/png" : "image/jpeg", version(bytes));
        } catch (IOException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "耗材图片不存在"); }
    }
    public void discardProductImage(String key) {
        try { Files.deleteIfExists(productPath(key)); }
        catch (IOException exception) { LoggerFactory.getLogger(PhotoStorageService.class).warn("Cannot remove rolled-back product photo {}", key, exception); }
    }
    public synchronized String savePetImage(long petId, MultipartFile file) {
        if (file.isEmpty()) throw invalidImage();
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 5 MB");
        Path temporary = null;
        try {
            byte[] bytes = file.getBytes();
            String contentType = validate(bytes);
            Path target = petPath(petId);
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "pet-", ".tmp");
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            stored.put(petKey(petId), new Metadata(contentType, version(bytes)));
            return petUrl(petId);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图片暂时无法保存，请稍后重试");
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
    public synchronized Image petImage(long petId) {
        var metadata = stored.get(petKey(petId));
        Path path = petPath(petId);
        try {
            if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "还没有宠物照片");
            byte[] bytes = Files.readAllBytes(path);
            String contentType = metadata != null ? metadata.contentType() : validate(bytes);
            String version = metadata != null ? metadata.version() : version(bytes);
            return new Image(bytes, contentType, version);
        } catch (IOException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片暂时无法读取"); }
    }
    public synchronized boolean hasPetImage(long petId) {
        if (stored.containsKey(petKey(petId))) return true;
        Path path = petPath(petId);
        if (!Files.isRegularFile(path)) return false;
        try {
            byte[] bytes = Files.readAllBytes(path);
            stored.put(petKey(petId), new Metadata(validate(bytes), version(bytes)));
            return true;
        } catch (Exception exception) { return false; }
    }
    public String petUrl(long petId) {
        var metadata = stored.get(petKey(petId));
        String version = metadata == null ? "1" : metadata.version();
        return "/api/media/pets/" + petId + "/image?v=" + version.substring(0, Math.min(12, version.length()));
    }
    private static String petKey(long petId) { return "pet-" + petId; }
    private Path petPath(long petId) { return directory.resolve("pets").resolve(petId + ".image"); }
    private Path productPath(String key) {
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "耗材图片不存在");
        return directory.resolve("products").resolve(key);
    }
    private static String validate(byte[] bytes) {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png").contains(format)) throw invalidImage();
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width > 6000 || height > 6000 || (long) width * height > 16_000_000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片尺寸过大，请缩小到宽高不超过 6000 像素、总像素不超过 1600 万");
                if (reader.read(0) == null) throw invalidImage();
                return format.equals("png") ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) { throw invalidImage(); }
    }
    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择完整、有效的 JPG 或 PNG 图片");
    }
    private static String version(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
