package com.campusconnect.agent.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * PaddleOCR 引擎 — 百度开源，中文 OCR 业界最优
 *
 * 通过 Python 子进程调用 PaddleOCR：
 *   python -c "from paddleocr import PaddleOCR; ocr = PaddleOCR(lang='ch'); ..."
 *
 * 优点：免费、离线可用、中文识别准确率 > 95%
 * 缺点：首次加载模型需 3-5 秒、需安装 Python + PaddleOCR
 */
@Slf4j
@Component
public class PaddleOcrProvider implements OcrProvider {

    /** Python 可执行文件路径 */
    @Value("${ocr.paddle.python-path:python}")
    private String pythonPath;

    /** 语言包（ch=中文简体+英文） */
    @Value("${ocr.paddle.lang:ch}")
    private String lang;

    /** 子进程超时（秒） */
    @Value("${ocr.paddle.timeout:60}")
    private int timeout;

    private volatile Boolean cachedAvailable;

    /** 执行 Python OCR 的脚本模板 */
    private static final String OCR_SCRIPT = ""
            + "import sys, json, time\n"
            + "from paddleocr import PaddleOCR\n"
            + "try:\n"
            + "    ocr = PaddleOCR(lang='%s', use_angle_cls=True, show_log=False)\n"
            + "    start = time.time()\n"
            + "    result = ocr.ocr(sys.argv[1], cls=True)\n"
            + "    elapsed = round((time.time() - start) * 1000)\n"
            + "    lines = []\n"
            + "    if result and result[0]:\n"
            + "        for line in result[0]:\n"
            + "            text = line[1][0]\n"
            + "            conf = line[1][1]\n"
            + "            lines.append(text)\n"
            + "    text = '\\n'.join(lines)\n"
            + "    print(json.dumps({'text': text, 'elapsed': elapsed, 'lines': len(lines)}, ensure_ascii=False))\n"
            + "except Exception as e:\n"
            + "    print(json.dumps({'error': str(e)}, ensure_ascii=False))\n"
            + "    sys.exit(1)\n";

    @Override
    public String getName() {
        return "PaddleOCR";
    }

    @Override
    public boolean isAvailable() {
        if (cachedAvailable != null) return cachedAvailable;

        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "-c", "from paddleocr import PaddleOCR; PaddleOCR(lang='ch', show_log=False)");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            cachedAvailable = finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("[OCR] PaddleOCR 不可用: {}", e.getMessage());
            cachedAvailable = false;
        }
        return cachedAvailable;
    }

    @Override
    public OcrResult recognize(File imageFile) {
        if (!imageFile.exists()) {
            return OcrResult.fail(getName(), "文件不存在: " + imageFile.getPath());
        }

        long start = System.currentTimeMillis();

        try {
            // 写临时 Python 脚本（因为模板太长，-c 可能受 shell 转义影响）
            Path scriptFile = Files.createTempFile("paddle_ocr_", ".py");
            String script = String.format(OCR_SCRIPT, lang);
            Files.writeString(scriptFile, script);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptFile.toString(), imageFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                output = sb.toString();
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            Files.deleteIfExists(scriptFile);

            if (!finished) {
                process.destroyForcibly();
                return OcrResult.fail(getName(), "OCR 超时（" + timeout + "秒）");
            }

            // 解析 JSON 结果
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(output);

            if (root.has("error")) {
                return OcrResult.fail(getName(), root.get("error").asText());
            }

            long elapsed = System.currentTimeMillis() - start;
            return OcrResult.success(
                    root.get("text").asText(),
                    getName(),
                    elapsed
            );

        } catch (Exception e) {
            log.error("[OCR] PaddleOCR 失败", e);
            return OcrResult.fail(getName(), e.getMessage());
        }
    }
}
