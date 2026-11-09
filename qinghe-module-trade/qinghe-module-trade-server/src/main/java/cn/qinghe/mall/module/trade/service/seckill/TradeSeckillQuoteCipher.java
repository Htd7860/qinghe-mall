package cn.qinghe.mall.module.trade.service.seckill;

import cn.qinghe.mall.module.trade.dal.dataobject.seckill.TradeSeckillQuoteDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static cn.qinghe.mall.framework.common.util.ServiceExceptionUtil.exception0;

/** 本项目PII边界的局部适配，非yudao原算法；AES-GCM随机nonce，身份/原号/期限作为AAD。 */
@Component
public class TradeSeckillQuoteCipher {
    private final String encodedKey;
    private final String keyId;
    private final SecureRandom random=new SecureRandom();
    public TradeSeckillQuoteCipher(@Value("${qinghe.trade.seckill-quote.key:}") String encodedKey,
            @Value("${qinghe.trade.seckill-quote.key-id:v1}") String keyId) {
        this.encodedKey=encodedKey;this.keyId=keyId; // 缺配置只拒绝报价操作，不阻止已有普通交易启动。
    }
    public String seal(TradeSeckillQuoteDO row,String json) {
        if (json==null || json.getBytes(StandardCharsets.UTF_8).length>65536) throw exception0(400,"秒杀报价快照超出支持范围");
        byte[] key=key();row.setKeyId(keyId);
        try {
            byte[] nonce=new byte[12];random.nextBytes(nonce);
            Cipher cipher=cipher(Cipher.ENCRYPT_MODE,key,nonce,row);
            byte[] encrypted=cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            byte[] packed=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,packed,0,nonce.length);
            System.arraycopy(encrypted,0,packed,nonce.length,encrypted.length);return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException ex) { throw exception0(503,"秒杀报价快照暂不可保存"); }
        finally { Arrays.fill(key,(byte)0); }
    }
    /** 后续trade建单/到期清理调用，不能将明文作为HTTP回包或日志。 */
    public String open(TradeSeckillQuoteDO row) {
        if (!Objects.equals(keyId,row.getKeyId()) || row.getSnapshotCipher()==null || row.getSnapshotCipher().length()>90000)
            throw exception0(503,"秒杀原报价解密配置或快照不可用");
        byte[] key=key();
        try {
            byte[] packed=Base64.getDecoder().decode(row.getSnapshotCipher());
            if (packed.length<29) throw new GeneralSecurityException("Invalid envelope");
            Cipher cipher=cipher(Cipher.DECRYPT_MODE,key,Arrays.copyOfRange(packed,0,12),row);
            return new String(cipher.doFinal(packed,12,packed.length-12),StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) { throw exception0(503,"秒杀原报价快照校验失败"); }
        finally { Arrays.fill(key,(byte)0); }
    }
    private Cipher cipher(int mode,byte[] key,byte[] nonce,TradeSeckillQuoteDO row) throws GeneralSecurityException {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        String aad="seckill-quote-v1|"+row.getTenantId()+"|"+row.getMemberId()+"|"+row.getQuoteId()+"|"+row.getRequestDigest()
                +"|"+row.getQuoteDigest()+"|"+row.getCreatedAt()+"|"+row.getExpiresAt()+"|"+row.getKeyId();
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));return cipher;
    }
    private byte[] key() {
        if (keyId==null || !keyId.matches("[A-Za-z0-9_-]{1,32}")) throw exception0(503,"秒杀报价加密配置不可用");
        try { byte[] key=Base64.getDecoder().decode(encodedKey);if (key.length==32) return key;Arrays.fill(key,(byte)0); }
        catch (IllegalArgumentException ignored) { }
        throw exception0(503,"秒杀报价加密配置不可用");
    }
}
