/*
* MIT License
*
* Copyright (c) 2020 高坚果
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
* */
package com.github.xiangyuecn.rsajava;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okio.base64.Base64;
import okio.base64.Base64Kt;

/**
 * RSA PEM格式秘钥对的解析和导出
 *
 * GitHub:https://github.com/xiangyuecn/RSA-java
 *
 * https://github.com/xiangyuecn/RSA-java/blob/master/RSA_PEM.java
 * 移植自：https://github.com/xiangyuecn/RSA-csharp/blob/master/RSA_PEM.cs
 */
public class RSA_PEM {
    /**modulus 模数，公钥、私钥都有**/
    public byte[] Key_Modulus;
    /**publicExponent 公钥指数，公钥、私钥都有**/
    public byte[] Key_Exponent;
    /**privateExponent 私钥指数，只有私钥的时候才有**/
    public byte[] Key_D;

    //以下参数只有私钥才有 https://docs.microsoft.com/zh-cn/dotnet/api/system.security.cryptography.rsaparameters?redirectedfrom=MSDN&view=netframework-4.8
    /**prime1**/
    public byte[] Val_P;
    /**prime2**/
    public byte[] Val_Q;
    /**exponent1**/
    public byte[] Val_DP;
    /**exponent2**/
    public byte[] Val_DQ;
    /**coefficient**/
    public byte[] Val_InverseQ;

    private RSA_PEM() {}
    /***
     * 通过公钥和私钥构造一个PEM
     * @param publicKey 必须提供公钥
     * @param privateKeyOrNull 私钥可以不提供，导出的PEM就只包含公钥
     **/
    public RSA_PEM(RSAPublicKey publicKey, RSAPrivateKey privateKeyOrNull) {
        this(
                BigB(publicKey.getModulus())
                , BigB(publicKey.getPublicExponent())
                , privateKeyOrNull==null?null:BigB(privateKeyOrNull.getPrivateExponent())
        );
    }
    /***
     * 通过全量的PEM字段数据构造一个PEM，除了模数modulus和公钥指数exponent必须提供外，其他私钥指数信息要么全部提供，要么全部不提供（导出的PEM就只包含公钥）
     * 注意：所有参数首字节如果是0，必须先去掉
     */
    public RSA_PEM(byte[] modulus, byte[] exponent, byte[] d, byte[] p, byte[] q, byte[] dp, byte[] dq, byte[] inverseQ) {
        Key_Modulus=modulus;
        Key_Exponent=exponent;
        Key_D=BigL(d, modulus.length);

        int keyLen = modulus.length / 2;
        Val_P=BigL(p, keyLen);
        Val_Q=BigL(q, keyLen);
        Val_DP=BigL(dp, keyLen);
        Val_DQ=BigL(dq, keyLen);
        Val_InverseQ=BigL(inverseQ, keyLen);
    }
    /***
     * 通过公钥指数和私钥指数构造一个PEM，会反推计算出P、Q但和原始生成密钥的P、Q极小可能相同
     * 注意：所有参数首字节如果是0，必须先去掉
     * @param modulus 必须提供模数
     * @param exponent 必须提供公钥指数
     * @param dOrNull 私钥指数可以不提供，导出的PEM就只包含公钥
     **/
    public RSA_PEM(byte[] modulus, byte[] exponent, byte[] dOrNull) {
        Key_Modulus=modulus;//modulus
        Key_Exponent=exponent;//publicExponent

        if(dOrNull!=null) {
            Key_D=BigL(dOrNull, modulus.length);//privateExponent

            //反推P、Q
            BigInteger n = BigX(modulus);
            BigInteger e = BigX(exponent);
            BigInteger d = BigX(dOrNull);
            BigInteger p = findFactor(e, d, n);
            BigInteger q = n.divide(p);
            if (p.compareTo(q) > 0) {
                BigInteger t = p;
                p = q;
                q = t;
            }
            BigInteger exp1 = d.mod(p.subtract(BigInteger.ONE));
            BigInteger exp2 = d.mod(q.subtract(BigInteger.ONE));
            BigInteger coeff = q.modInverse(p);

            int keyLen = modulus.length / 2;
            Val_P=BigL(BigB(p), keyLen);//prime1
            Val_Q=BigL(BigB(q), keyLen);//prime2
            Val_DP=BigL(BigB(exp1), keyLen);//exponent1
            Val_DQ=BigL(BigB(exp2), keyLen);//exponent2
            Val_InverseQ=BigL(BigB(coeff), keyLen);//coefficient
        }
    }

    /**秘钥位数**/
    public int keySize(){
        return Key_Modulus.length*8;
    }
    /**是否包含私钥**/
    public boolean hasPrivate(){
        return Key_D!=null;
    }
    /**得到公钥Java对象**/
    public RSAPublicKey getRSAPublicKey() throws Exception {
        RSAPublicKeySpec spec=new RSAPublicKeySpec(BigX(Key_Modulus), BigX(Key_Exponent));
        KeyFactory factory=KeyFactory.getInstance("RSA");
        return (RSAPublicKey)factory.generatePublic(spec);
    }
    /**得到私钥Java对象**/
    public RSAPrivateKey getRSAPrivateKey() throws Exception {
        if(Key_D==null) {
            throw new Exception("当前为公钥，无法获得私钥");
        }
        RSAPrivateKeySpec spec=new RSAPrivateKeySpec(BigX(Key_Modulus), BigX(Key_D));
        KeyFactory factory=KeyFactory.getInstance("RSA");
        return (RSAPrivateKey)factory.generatePrivate(spec);
    }
    /**转成正整数，如果是负数，需要加前导0转成正整数**/
    static public BigInteger BigX(byte[] bigb) {
        if(bigb[0]<0) {
            byte[] c=new byte[bigb.length+1];
            System.arraycopy(bigb,0,c,1,bigb.length);
            bigb=c;
        }
        return new BigInteger(bigb);
    }
    /**BigInt导出byte整数首字节>0x7F的会加0前导，保证正整数，因此需要去掉0**/
    static public byte[] BigB(BigInteger bigx) {
        byte[] val=bigx.toByteArray();
        if(val[0]==0) {
            byte[] c=new byte[val.length-1];
            System.arraycopy(val,1,c,0,c.length);
            val=c;
        }
        return val;
    }
    /**某些密钥参数可能会少一位（32个byte只有31个，目测是密钥生成器的问题，只在c#生成的密钥中发现这种参数，java中生成的密钥没有这种现象），直接修正一下就行；这个问题与BigB有本质区别，不能动BigB**/
    static public byte[] BigL(byte[] bytes, int keyLen) {
        if (keyLen - bytes.length == 1) {
            byte[] c = new byte[bytes.length + 1];
            System.arraycopy(bytes, 0, c, 1, bytes.length);
            bytes = c;
        }
        return bytes;
    }
    /**
     * 由n e d 反推 P Q
     * 资料： https://stackoverflow.com/questions/43136036/how-to-get-a-rsaprivatecrtkey-from-a-rsaprivatekey
     * https://v2ex.com/t/661736
     ***/
    private static BigInteger findFactor(BigInteger e, BigInteger d, BigInteger n) {
        BigInteger edMinus1 = e.multiply(d).subtract(BigInteger.ONE);
        int s = edMinus1.getLowestSetBit();
        BigInteger t = edMinus1.shiftRight(s);

        long now=System.currentTimeMillis();
        for (int aInt = 2; true; aInt++) {
            if(aInt%10==0 && System.currentTimeMillis()-now>3000) {
                throw new RuntimeException("推算RSA.P超时");//测试最多循环2次，1024位的速度很快 8ms
            }

            BigInteger aPow = BigInteger.valueOf(aInt).modPow(t, n);
            for (int i = 1; i <= s; i++) {
                if (aPow.equals(BigInteger.ONE)) {
                    break;
                }
                if (aPow.equals(n.subtract(BigInteger.ONE))) {
                    break;
                }
                BigInteger aPowSquared = aPow.multiply(aPow).mod(n);
                if (aPowSquared.equals(BigInteger.ONE)) {
                    return aPow.subtract(BigInteger.ONE).gcd(n);
                }
                aPow = aPowSquared;
            }
        }
    }











    static public RSA_PEM FromPEM(char[] base64, boolean privateKey) throws Exception {
        RSA_PEM param=new RSA_PEM();

        byte[] dataX = Base64Kt.decodeBase64ToArray(base64);
        if (dataX == null) {
            throw new Exception("PEM内容无效");
        }
        short[] data=new short[dataX.length];//转成正整数的bytes数组，不然byte是负数难搞
        for(int i=0;i<dataX.length;i++) {
            data[i]=(short)(dataX[i]&0xff);
        }

        int[] idx = new int[] {0};

        if (!privateKey) {
            /****使用公钥****/
            //读取数据总长度
            readLen(0x30, data, idx);

            //检测PKCS8
            int[] idx2 = new int[] {idx[0]};
            if (eq(_SeqOID, data, idx)) {
                //读取1长度
                readLen(0x03, data, idx);
                idx[0]++;//跳过0x00
                //读取2长度
                readLen(0x30, data, idx);
            }else {
                idx = idx2;
            }

            //Modulus
            param.Key_Modulus = readBlock(data, idx);

            //Exponent
            param.Key_Exponent = readBlock(data, idx);
        } else {
            /****使用私钥****/
            //读取数据总长度
            readLen(0x30, data, idx);

            //读取版本号
            if (!eq(_Ver, data, idx)) {
                throw new Exception("PEM未知版本");
            }

            //检测PKCS8
            int[] idx2 = new int[] {idx[0]};
            if (eq(_SeqOID, data, idx)) {
                //读取1长度
                readLen(0x04, data, idx);
                //读取2长度
                readLen(0x30, data, idx);

                //读取版本号
                if (!eq(_Ver, data, idx)) {
                    throw new Exception("PEM版本无效");
                }
            } else {
                idx = idx2;
            }

            //读取数据
            param.Key_Modulus = readBlock(data, idx);
            param.Key_Exponent = readBlock(data, idx);
            int keyLen = param.Key_Modulus.length;
            param.Key_D = BigL(readBlock(data, idx), keyLen);
            keyLen = keyLen / 2;
            param.Val_P = BigL(readBlock(data, idx), keyLen);
            param.Val_Q = BigL(readBlock(data, idx), keyLen);
            param.Val_DP = BigL(readBlock(data, idx), keyLen);
            param.Val_DQ = BigL(readBlock(data, idx), keyLen);
            param.Val_InverseQ = BigL(readBlock(data, idx), keyLen);
        }

        return param;
    }

    /**
     * 用PEM格式密钥对创建RSA，支持PKCS#1、PKCS#8格式的PEM
     */
    static public RSA_PEM FromPEM(String pem) throws Exception {
        String base64 = _PEMCode.matcher(pem).replaceAll("");

        if (pem.contains("PUBLIC KEY")) {
            return FromPEM(base64.toCharArray(), false);
        } else if (pem.contains("PRIVATE KEY")) {
            return FromPEM(base64.toCharArray(), true);
        } else {
            throw new Exception("pem需要BEGIN END标头");
        }
    }
    static private Pattern _PEMCode = Pattern.compile("--+.+?--+|[\\s\\r\\n]+");
    static private byte[] _SeqOID = new byte[] { 0x30, 0x0D, 0x06, 0x09, 0x2A, (byte)0x86, 0x48, (byte)0x86, (byte)0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00 };
    static private byte[] _Ver = new byte[] { 0x02, 0x01, 0x00 };

    /**从数组start开始到指定长度复制一份**/
    static private byte[] sub(short[] arr, int start, int count) {
        byte[] val = new byte[count];
        for (int i = 0; i < count; i++) {
            val[i] = (byte)arr[start + i];
        }
        return val;
    }
    /**读取长度**/
    static private int readLen(int first, short[] data, int[] idxO) throws Exception {
        int idx=idxO[0];
        try {
            if (data[idx] == first) {
                idx++;
                if (data[idx] == 0x81) {
                    idx++;
                    return data[idx++];
                } else if (data[idx] == 0x82) {
                    idx++;
                    return (((int)data[idx++]) << 8) + data[idx++];
                } else if (data[idx] < 0x80) {
                    return data[idx++];
                }
            }
            throw new Exception("PEM未能提取到数据");
        }finally {
            idxO[0]=idx;
        }
    }
    /**读取块数据**/
    static private byte[] readBlock(short[] data, int[] idxO) throws Exception {
        int idx=idxO[0];
        try {
            int len = readLen(0x02, data, idxO);
            idx=idxO[0];
            if (data[idx] == 0x00) {
                idx++;
                len--;
            }
            byte[] val = sub(data, idx, len);
            idx += len;
            return val;
        }finally {
            idxO[0]=idx;
        }
    }
    /**比较data从idx位置开始是否是byts内容**/
    static private boolean eq(byte[] byts, short[] data, int[] idxO) {
        int idx=idxO[0];
        try {
            for (int i = 0; i < byts.length; i++, idx++) {
                if (idx >= data.length) {
                    return false;
                }
                if ((byts[i]&0xff) != data[idx]) {
                    return false;
                }
            }
            return true;
        }finally {
            idxO[0]=idx;
        }
    }


















    public byte[] ToPEM_PKCS1_Bytes(boolean convertToPublic) throws Exception {
        return ToPEM_Bytes(convertToPublic, false, false);
    }

    public byte[] ToPEM_PKCS8_Bytes(boolean convertToPublic) throws Exception {
        return ToPEM_Bytes(convertToPublic, true, true);
    }

    public byte[] ToPEM_Bytes(boolean convertToPublic, boolean privateUsePKCS8, boolean publicUsePKCS8) throws Exception {
        //https://www.jianshu.com/p/25803dd9527d
        //https://www.cnblogs.com/ylz8401/p/8443819.html
        //https://blog.csdn.net/jiayanhui2877/article/details/47187077
        //https://blog.csdn.net/xuanshao_/article/details/51679824
        //https://blog.csdn.net/xuanshao_/article/details/51672547

        ByteArrayOutputStream ms = new ByteArrayOutputStream();


        if (this.Key_D==null || convertToPublic) {
            /****生成公钥****/

            //写入总字节数，不含本段长度，额外需要24字节的头，后续计算好填入
            ms.write(0x30);
            int index1 = ms.size();

            //PKCS8 多一段数据
            int index2 = -1, index3 = -1;
            if (publicUsePKCS8) {
                //固定内容
                // encoded OID sequence for PKCS #1 rsaEncryption szOID_RSA_RSA = "1.2.840.113549.1.1.1"
                ms.write(_SeqOID);

                //从0x00开始的后续长度
                ms.write(0x03);
                index2 = ms.size();
                ms.write(0x00);

                //后续内容长度
                ms.write(0x30);
                index3 = ms.size();
            }

            //写入Modulus
            writeBlock(Key_Modulus, ms);

            //写入Exponent
            writeBlock(Key_Exponent, ms);


            //计算空缺的长度
            byte[] byts = ms.toByteArray();

            if (index2 != -1) {
                byts = writeLen(index3, byts, ms);
                byts = writeLen(index2, byts, ms);
            }
            byts = writeLen(index1, byts, ms);

            return Base64Kt.encodeBase64ToByteArray(byts, Base64.Default.INSTANCE);
        } else {
            /****生成私钥****/

            //写入总字节数，后续写入
            ms.write(0x30);
            int index1 = ms.size();

            //写入版本号
            ms.write(_Ver);

            //PKCS8 多一段数据
            int index2 = -1, index3 = -1;
            if (privateUsePKCS8) {
                //固定内容
                ms.write(_SeqOID);

                //后续内容长度
                ms.write(0x04);
                index2 = ms.size();

                //后续内容长度
                ms.write(0x30);
                index3 = ms.size();

                //写入版本号
                ms.write(_Ver);
            }

            //写入数据
            writeBlock(Key_Modulus, ms);
            writeBlock(Key_Exponent, ms);
            writeBlock(Key_D, ms);
            writeBlock(Val_P, ms);
            writeBlock(Val_Q, ms);
            writeBlock(Val_DP, ms);
            writeBlock(Val_DQ, ms);
            writeBlock(Val_InverseQ, ms);


            //计算空缺的长度
            byte[] byts = ms.toByteArray();

            if (index2 != -1) {
                byts = writeLen(index3, byts, ms);
                byts = writeLen(index2, byts, ms);
            }
            byts = writeLen(index1, byts, ms);

            return Base64Kt.encodeBase64ToByteArray(byts, Base64.Default.INSTANCE);
        }
    }

    /***
     * 将RSA中的密钥对转换成PEM PKCS#8格式
     * 。convertToPublic：等于true时含私钥的RSA将只返回公钥，仅含公钥的RSA不受影响
     * 。公钥如：-----BEGIN RSA PUBLIC KEY-----，私钥如：-----BEGIN RSA PRIVATE KEY-----
     * 。似乎导出PKCS#1公钥用的比较少，PKCS#8的公钥用的多些，私钥#1#8都差不多
     */
    public String ToPEM_PKCS1(boolean convertToPublic) throws Exception {
        return ToPEM(convertToPublic, false, false);
    }
    /***
     * 将RSA中的密钥对转换成PEM PKCS#8格式
     * 。convertToPublic：等于true时含私钥的RSA将只返回公钥，仅含公钥的RSA不受影响
     * 。公钥如：-----BEGIN PUBLIC KEY-----，私钥如：-----BEGIN PRIVATE KEY-----
     */
    public String ToPEM_PKCS8(boolean convertToPublic) throws Exception {
        return ToPEM(convertToPublic, true, true);
    }
    /***
     * 将RSA中的密钥对转换成PEM格式
     * 。convertToPublic：等于true时含私钥的RSA将只返回公钥，仅含公钥的RSA不受影响
     * 。privateUsePKCS8：私钥的返回格式，等于true时返回PKCS#8格式（-----BEGIN PRIVATE KEY-----），否则返回PKCS#1格式（-----BEGIN RSA PRIVATE KEY-----），返回公钥时此参数无效；两种格式使用都比较常见
     * 。publicUsePKCS8：公钥的返回格式，等于true时返回PKCS#8格式（-----BEGIN PUBLIC KEY-----），否则返回PKCS#1格式（-----BEGIN RSA PUBLIC KEY-----），返回私钥时此参数无效；一般用的多的是true PKCS#8格式公钥，PKCS#1格式公钥似乎比较少见
     */
    public String ToPEM(boolean convertToPublic, boolean privateUsePKCS8, boolean publicUsePKCS8) throws Exception {
        byte[] byts = ToPEM_Bytes(convertToPublic, privateUsePKCS8, publicUsePKCS8);

        String flag = "";
        if (this.Key_D==null || convertToPublic) {
            flag = " PUBLIC KEY";
            if (!publicUsePKCS8) {
                flag = " RSA" + flag;
            }
        } else {
            flag = " PRIVATE KEY";
            if (!privateUsePKCS8) {
                flag = " RSA" + flag;
            }
        }

        //noinspection CharsetObjectCanBeUsed
        return "-----BEGIN" + flag + "-----\n" + TextBreak(new String(byts, Charset.forName("UTF-8")), 64) + "\n-----END" + flag + "-----";
    }
    /**写入一个长度字节码**/
    static private void writeLenByte(int len, ByteArrayOutputStream ms) {
        if (len < 0x80) {
            ms.write((byte)len);
        } else if (len <= 0xff) {
            ms.write(0x81);
            ms.write((byte)len);
        } else {
            ms.write(0x82);
            ms.write((byte)(len >> 8 & 0xff));
            ms.write((byte)(len & 0xff));
        }
    }
    /**写入一块数据**/
    static private void writeBlock(byte[] byts, ByteArrayOutputStream ms) throws Exception {
        boolean addZero = ((byts[0] & 0xff) >> 4) >= 0x8;
        ms.write(0x02);
        int len = byts.length + (addZero ? 1 : 0);
        writeLenByte(len, ms);

        if (addZero) {
            ms.write(0x00);
        }
        ms.write(byts);
    }
    /**根据后续内容长度写入长度数据**/
    static private byte[] writeLen(int index, byte[] byts, ByteArrayOutputStream ms) {
        int len = byts.length - index;

        ms.reset();
        ms.write(byts, 0, index);
        writeLenByte(len, ms);
        ms.write(byts, index, len);

        return ms.toByteArray();
    }
    /**把字符串按每行多少个字断行**/
    static private String TextBreak(String text, int line) {
        int idx = 0;
        int len = text.length();
        StringBuilder str = new StringBuilder();
        while (idx < len) {
            if (idx > 0) {
                str.append('\n');
            }
            if (idx + line >= len) {
                str.append(text.substring(idx));
            } else {
                str.append(text.substring(idx, idx+line));
            }
            idx += line;
        }
        return str.toString();
    }





















    /***
     * 将XML格式密钥转成PEM，支持公钥xml、私钥xml
     */
    static public RSA_PEM FromXML(String xml) throws Exception {
        RSA_PEM rtv=new RSA_PEM();

        Matcher xmlM=xmlExp.matcher(xml);
        if(!xmlM.find()) {
            throw new Exception("XML内容不符合要求");
        }

        Matcher tagM=xmlTagExp.matcher(xmlM.group(1));
//        Base64.Decoder dec=Base64.getDecoder();
        while(tagM.find()) {
            String tag=tagM.group(1);
            String b64=tagM.group(2);
            byte[] val=Base64Kt.decodeBase64ToArray(b64);
            switch(tag) {
                case "Modulus":rtv.Key_Modulus=val;break;
                case "Exponent":rtv.Key_Exponent=val;break;
                case "D":rtv.Key_D=val;break;

                case "P":rtv.Val_P=val;break;
                case "Q":rtv.Val_Q=val;break;
                case "DP":rtv.Val_DP=val;break;
                case "DQ":rtv.Val_DQ=val;break;
                case "InverseQ":rtv.Val_InverseQ=val;break;
            }
        }

        if(rtv.Key_Modulus==null || rtv.Key_Exponent==null) {
            throw new Exception("XML公钥丢失");
        }
        if(rtv.Key_D!=null) {
            if(rtv.Val_P==null || rtv.Val_Q==null || rtv.Val_DP==null || rtv.Val_DQ==null || rtv.Val_InverseQ==null) {
                return new RSA_PEM(rtv.Key_Modulus, rtv.Key_Exponent, rtv.Key_D);
            }
        }

        return rtv;
    }
    static private Pattern xmlExp=Pattern.compile("\\s*<RSAKeyValue>([<>\\/\\+=\\w\\s]+)</RSAKeyValue>\\s*");
    static private Pattern xmlTagExp=Pattern.compile("<(.+?)>\\s*([^<]+?)\\s*</");






    /***
     * 将RSA中的密钥对转换成XML格式
     * ，如果convertToPublic含私钥的RSA将只返回公钥，仅含公钥的RSA不受影响
     */
    public String ToXML(boolean convertToPublic) {
        StringBuilder str=new StringBuilder();
        str.append("<RSAKeyValue>");
        str.append("<Modulus>"+Base64Kt.encodeBase64(Key_Modulus, Base64.Default.INSTANCE)+"</Modulus>");
        str.append("<Exponent>"+Base64Kt.encodeBase64(Key_Exponent, Base64.Default.INSTANCE)+"</Exponent>");
        if (this.Key_D==null || convertToPublic) {
            /****生成公钥****/
            //NOOP
        } else {
            /****生成私钥****/
            str.append("<P>"+Base64Kt.encodeBase64(Val_P, Base64.Default.INSTANCE)+"</P>");
            str.append("<Q>"+Base64Kt.encodeBase64(Val_Q, Base64.Default.INSTANCE)+"</Q>");
            str.append("<DP>"+Base64Kt.encodeBase64(Val_DP, Base64.Default.INSTANCE)+"</DP>");
            str.append("<DQ>"+Base64Kt.encodeBase64(Val_DQ, Base64.Default.INSTANCE)+"</DQ>");
            str.append("<InverseQ>"+Base64Kt.encodeBase64(Val_InverseQ, Base64.Default.INSTANCE)+"</InverseQ>");
            str.append("<D>"+Base64Kt.encodeBase64(Key_D, Base64.Default.INSTANCE)+"</D>");
        }
        str.append("</RSAKeyValue>");
        return str.toString();
    }
}
