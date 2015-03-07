package kanjava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;

@SpringBootApplication
@RestController
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class); // 後で使う

    @Autowired
    FaceDetector faceDetector;
    @Autowired
    JmsMessagingTemplate jmsMessagingTemplate; // メッセージ操作用APIのJMSラッパー

    @RequestMapping(value = "/send")
    String send(@RequestParam String msg /* リクエストパラメータmsgでメッセージ本文を受け取る */) {
        Message<String> message = MessageBuilder
                .withPayload(msg)
                .build(); // メッセージを作成
        jmsMessagingTemplate.send("hello", message); // 宛先helloにメッセージを送信
        return "OK"; // とりあえずOKと即時応答しておく
    }

    @JmsListener(destination = "hello", concurrency = "1-5" /* 最小1スレッド、最大5スレッドに設定 */)
    void handleHelloMessage(Message<String> message /* 送信されたメッセージを受け取る */) {
        log.info("received! {}", message);
        log.info("msg={}", message.getPayload());
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @RequestMapping(value = "/")
    String hello() {
        return "OK!日本語通ったね！";
    }

}

@Component // コンポーネントスキャン対象にする。@Serviceでも@NamedでもOK
class FaceDetector {
    // 分類器のパスをプロパティから取得できるようにする
    @Value("${classifierFile:classpath:/haarcascade_frontalface_default.xml}")
    File classifierFile;

    CascadeClassifier classifier;

    static final Logger log = LoggerFactory.getLogger(FaceDetector.class);

    public void detectFaces(Mat source, BiConsumer<Mat, Rect> detectAction) {
        // 顔認識結果
        Rect faceDetections = new Rect();
        // 顔認識実行
        classifier.detectMultiScale(source, faceDetections);
        // 認識した顔の数
        int numOfFaces = faceDetections.limit();
        log.info("{} faces are detected!", numOfFaces);
        for (int i = 0; i < numOfFaces; i++) {
            // i番目の認識結果
            Rect r = faceDetections.position(i);
            // 1件ごとの認識結果を変換処理(関数)にかける
            detectAction.accept(source, r);
        }
    }

    @PostConstruct // 初期化処理。DIでプロパティがセットされたあとにclassifierインスタンスを生成したいのでここで書く。
    void init() throws IOException {
        if (log.isInfoEnabled()) {
            log.info("load {}", classifierFile.toPath());
        }
        // 分類器の読み込み
        this.classifier = new CascadeClassifier(classifierFile.toPath()
                .toString());
    }
}

class FaceTranslator {
    public static void duker(Mat source, Rect r) { // BiConsumer<Mat, Rect>で渡せるようにする
        int x = r.x(), y = r.y(), h = r.height(), w = r.width();
        // Dukeのように描画する
        // 上半分の黒四角
        rectangle(source, new Point(x, y), new Point(x + w, y + h / 2),
                new Scalar(0, 0, 0, 0), -1, CV_AA, 0);
        // 下半分の白四角
        rectangle(source, new Point(x, y + h / 2), new Point(x + w, y + h),
                new Scalar(255, 255, 255, 0), -1, CV_AA, 0);
        // 中央の赤丸
        circle(source, new Point(x + h / 2, y + h / 2), (w + h) / 12,
                new Scalar(0, 0, 255, 0), -1, CV_AA, 0);
    }
}
