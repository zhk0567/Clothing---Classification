package com.deepfashion.classifier

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import androidx.preference.PreferenceManager

class DeepFashionClassifier(private val context: Context) {
    
    private var ortSession: OrtSession? = null
    private val inputSize = 224
    private val numClasses = 50
    private var lastPerfThreadsEnabled: Boolean? = null
    
    // DeepFashion 50个类别的中文名称（对应训练时的50个类别）
    private val categories = listOf(
        "Anorak", "Blazer", "Blouse", "Bomber", "Button-Down", "Cardigan", "Flannel", "Halter",
        "Henley", "Hoodie", "Jacket", "Jersey", "Parka", "Peacoat", "Poncho", "Sweater", "Tank",
        "Tee", "Top", "Turtleneck", "Capris", "Chinos", "Culottes", "Cutoffs", "Gauchos", "Jeans",
        "Jeggings", "Jodhpurs", "Joggers", "Leggings", "Sarong", "Shorts", "Skirt", "Sweatpants",
        "Sweatshorts", "Trunks", "Caftan", "Cape", "Coat", "Coverup", "Dress", "Jumpsuit", "Kaftan",
        "Kimono", "Nightdress", "Onesie", "Robe", "Romper", "Shirtdress", "Sundress"
    )

    // 中文类别映射
    private val categoryChinese = mapOf(
        "Anorak" to "冲锋衣", "Blazer" to "西装外套", "Blouse" to "女式衬衫", "Bomber" to "飞行员夹克",
        "Button-Down" to "纽扣衫", "Cardigan" to "开衫", "Flannel" to "法兰绒衬衫", "Halter" to "挂脖上衣",
        "Henley" to "半开领衫", "Hoodie" to "连帽衫", "Jacket" to "夹克", "Jersey" to "运动衫",
        "Parka" to "派克大衣", "Peacoat" to "双排扣大衣", "Poncho" to "斗篷", "Sweater" to "毛衣",
        "Tank" to "背心", "Tee" to "T恤", "Top" to "上衣", "Turtleneck" to "高领衫",
        "Capris" to "七分裤", "Chinos" to "卡其裤", "Culottes" to "阔腿短裤", "Cutoffs" to "牛仔短裤",
        "Gauchos" to "高乔裤", "Jeans" to "牛仔裤", "Jeggings" to "打底裤", "Jodhpurs" to "马裤",
        "Joggers" to "慢跑裤", "Leggings" to "紧身裤", "Sarong" to "纱笼", "Shorts" to "短裤",
        "Skirt" to "裙子", "Sweatpants" to "运动裤", "Sweatshorts" to "运动短裤", "Trunks" to "泳裤",
        "Caftan" to "长袍", "Cape" to "斗篷", "Coat" to "外套", "Coverup" to "罩衫",
        "Dress" to "连衣裙", "Jumpsuit" to "连体衣", "Kaftan" to "长袍", "Kimono" to "和服",
        "Nightdress" to "睡裙", "Onesie" to "连体服", "Robe" to "长袍", "Romper" to "连体短裤",
        "Shirtdress" to "衬衫裙", "Sundress" to "太阳裙"
    )

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val ortEnv = OrtEnvironment.getEnvironment()
            
            // 加载ONNX模型（根据设置判断是否启用本地模型）
            val modelBytes = context.assets.open("models/deepfashion_classifier.onnx").readBytes()
            val sessionOptions = OrtSession.SessionOptions()
            
            // 从设置读取性能偏好，尽量设置线程数（若API不可用则忽略）
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val perfThreads = prefs.getBoolean("pref_performance_threads", true)
            if (perfThreads) {
                val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
                try {
                    sessionOptions.setIntraOpNumThreads(threads)
                } catch (_: Throwable) { }
                try {
                    sessionOptions.setInterOpNumThreads(threads)
                } catch (_: Throwable) { }
            } else {
                try { sessionOptions.setIntraOpNumThreads(1) } catch (_: Throwable) { }
                try { sessionOptions.setInterOpNumThreads(1) } catch (_: Throwable) { }
            }
            
            ortSession = ortEnv.createSession(modelBytes, sessionOptions)
            lastPerfThreadsEnabled = perfThreads
            
        } catch (e: Exception) {
            Log.e("DeepFashionClassifier", "模型加载失败", e)
            throw RuntimeException("无法加载DeepFashion模型", e)
        }
    }

    private fun ensureSessionUpToDate() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val perfThreads = prefs.getBoolean("pref_performance_threads", true)
        val needReload = (lastPerfThreadsEnabled == null) || (lastPerfThreadsEnabled != perfThreads) || (ortSession == null)
        if (needReload) {
            try {
                ortSession?.close()
            } catch (_: Exception) { }
            ortSession = null
            loadModel()
        }
    }

    fun classifyImage(bitmap: Bitmap): ClassificationResult {
        ensureSessionUpToDate()
        val session = this.ortSession ?: throw RuntimeException("模型未加载")
        
        var inputTensor: OnnxTensor? = null
        var outputTensor: OnnxTensor? = null
        
        try {
            // 预处理图像 - ONNX需要NCHW格式 (1, 3, 224, 224)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputArray = bitmapToFloatArray(resizedBitmap)
            
            // 转换为FloatBuffer
            val inputBuffer = ByteBuffer.allocateDirect(inputArray.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            inputBuffer.put(inputArray)
            inputBuffer.rewind()
            
            // 创建ONNX输入
            val ortEnv = OrtEnvironment.getEnvironment()
            val inputName = session.inputNames.iterator().next()
            val inputShape = longArrayOf(1, 3, 224, 224)
            inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)
        
        // 运行推理
            val inputs = mutableMapOf<String, OnnxTensorLike>()
            inputs[inputName] = inputTensor
            val outputs = session.run(inputs)

            // 获取输出（优先按索引获取，某些版本不支持名称索引）
            val outputName = session.outputNames.iterator().next()
            val firstOutput: OnnxValue = try {
                // 某些版本按名称获取返回 Optional，需要处理为空的情况
                outputs.get(outputName).orElse(outputs.get(0))
            } catch (ex: Exception) {
                outputs.get(0)
            }

            outputTensor = (firstOutput as? OnnxTensor)
                ?: throw RuntimeException("输出格式错误")

            val outputBuffer = outputTensor.floatBuffer
            outputBuffer.rewind()
            
            // 读取输出结果
            val probabilities = FloatArray(numClasses)
            outputBuffer.get(probabilities)
            
            // 清理资源
            outputs.close()
            if (inputTensor != null) inputTensor.close()
            if (outputTensor != null) outputTensor.close()
        
        // 找到最高概率的类别
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val confidence = probabilities[maxIndex]
        
            // 转换为softmax概率
            val softmaxProb = softmax(probabilities)[maxIndex]
            
            val categoryEn = if (maxIndex < categories.size) categories[maxIndex] else "Unknown"
            // 语言设置：仅 zh / en（默认 zh）
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val lang = prefs.getString("pref_language", "zh") ?: "zh"
            val isZh = lang == "zh"
            val category = if (isZh) (categoryChinese[categoryEn] ?: categoryEn) else categoryEn
            val description = getCategoryDescriptionLocalized(categoryEn, isZh)
            
            return ClassificationResult(category, softmaxProb, description)
            
        } catch (e: Exception) {
            // 确保资源清理
            try {
                inputTensor?.close()
                outputTensor?.close()
            } catch (_: Exception) { }
            Log.e("DeepFashionClassifier", "分类失败", e)
            throw RuntimeException("分类失败", e)
        }
    }

    private fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        // ONNX需要NCHW格式: (1, 3, 224, 224)
        // 需要ImageNet标准化
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        val floatBuffer = FloatArray(3 * inputSize * inputSize)
        
        // ImageNet标准化参数
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        
        // 转换为NCHW格式
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            
            // ImageNet标准化
            floatBuffer[i] = (r - mean[0]) / std[0]  // R channel
            floatBuffer[inputSize * inputSize + i] = (g - mean[1]) / std[1]  // G channel
            floatBuffer[2 * inputSize * inputSize + i] = (b - mean[2]) / std[2]  // B channel
        }
        
        return floatBuffer
    }
    
    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exp = logits.map { kotlin.math.exp(it - max) }
        val sum = exp.sum()
        return exp.map { (it / sum).toFloat() }.toFloatArray()
    }

    private fun getCategoryDescriptionLocalized(categoryEn: String, isZh: Boolean): String {
        fun zh(style: String, scene: String, pair: String, season: String, care: String): String {
            return "风格: $style\n场景: $scene\n搭配: $pair\n季节材质: $season\n护理: $care"
        }
        fun en(style: String, scene: String, pair: String, season: String, care: String): String {
            return "Style: $style\nOccasions: $scene\nPairing: $pair\nSeason/Fabric: $season\nCare: $care"
        }
        val c = categoryEn
        return when (c) {
            // Tops / Outerwear
            "Anorak" -> if (isZh) zh("防风防雨户外外套", "户外徒步、通勤雨天", "功能T恤/抓绒 + 登山鞋", "春秋防风面料，部分三合一结构", "避强力机洗，低温悬挂阴干") else en("Waterproof windproof shell", "Hiking, rainy commute", "Tech tee/fleece + hiking boots", "Spring/Fall shells; some 3-in-1", "Gentle wash, hang dry")
            "Blazer" -> if (isZh) zh("修身/宽松西装版", "商务通勤、半正式", "衬衫或针织 + 西裤/牛仔", "四季面料，夏薄呢冬呢料/混纺", "建议干洗，悬挂保存") else en("Tailored/relaxed suit jacket", "Business casual, semi-formal", "Shirt/knit + trousers/jeans", "All-season fabrics", "Dry clean preferred")
            "Blouse" -> if (isZh) zh("女性化细节上衣", "通勤、约会、半正式", "半裙/西裤；可叠穿针织", "棉/真丝/雪纺", "温和清洗，真丝低温手洗") else en("Feminine dress shirt", "Work, date, semi-formal", "Skirt/trousers; layer with knit", "Cotton/silk/chiffon", "Delicate wash; hand-wash silk")
            "Bomber" -> if (isZh) zh("短款罗纹门襟", "通勤休闲", "T恤 + 牛仔/工装裤", "春秋尼龙/涤纶壳面", "反面冷水洗") else en("Short ribbed hem/neck", "Casual commute", "Tee + jeans/cargo", "Nylon/poly shell", "Cold wash inside-out")
            "Button-Down" -> if (isZh) zh("门襟纽扣衬衫", "商务/通勤/休闲", "西裤/牛仔；可内搭针织", "棉/混纺", "低温熨烫，分色洗") else en("Button placket shirt", "Business/casual", "Trousers/jeans; layer with knit", "Cotton/blends", "Low-temp iron")
            "Cardigan" -> if (isZh) zh("V/圆领开衫", "春秋外搭/空调房", "打底T/衬衫 + 半裙/牛仔", "棉/羊毛/羊绒", "平铺阴干") else en("Open-front knit", "Layering/AC rooms", "Base tee/shirt + skirt/jeans", "Cotton/wool/cashmere", "Lay flat to dry")
            "Flannel" -> if (isZh) zh("厚绒格纹衬衫", "秋冬通勤休闲", "内搭T或单穿", "法兰绒棉料", "低温机洗") else en("Brushed plaid shirt", "Casual fall/winter", "Layer tee or wear alone", "Flannel cotton", "Cold gentle wash")
            "Halter" -> if (isZh) zh("挂脖露肩", "夏季出游/聚会", "半裙/短裤 + 凉鞋", "雪纺/丝缎/针织", "手洗避免暴晒") else en("Halter-neck", "Summer outings/parties", "Skirt/shorts + sandals", "Chiffon/satin/knit", "Hand wash; avoid sun")
            "Henley" -> if (isZh) zh("半开门襟圆领", "通勤与休闲", "牛仔/工装裤；叠穿外套", "棉/针织", "避免拉伸领口") else en("Round neck with short placket", "Casual/work", "Jeans/cargo; layer jacket", "Cotton/knit", "Avoid neckline stretch")
            "Hoodie" -> if (isZh) zh("连帽抓绒/毛圈", "通学、旅行、居家", "牛仔/运动裤 + 运动鞋", "棉/抓绒", "反面洗，适度烘干") else en("Hooded fleece/loopback", "School, travel, home", "Jeans/sweats + sneakers", "Cotton/fleece", "Inside-out wash")
            "Jacket" -> if (isZh) zh("短款外套统称", "通勤/出行", "T恤或衬衫 + 牛仔/西裤", "春秋多样面料", "按材质清洗") else en("Short outerwear", "Commute/outings", "Tee/shirt + jeans/trousers", "Varied fabrics", "Follow fabric care")
            "Jersey" -> if (isZh) zh("运动面料套头", "运动/通勤休闲", "运动裤/牛仔 + 运动鞋", "涤纶/棉混", "冷水洗，少柔顺剂") else en("Athletic knit", "Sport/casual", "Sweats/jeans + sneakers", "Poly/cotton blend", "Cold wash; minimal softener")
            "Parka" -> if (isZh) zh("中长保暖连帽", "冬季通勤/户外", "针织 + 牛仔/工装", "羽绒/厚棉/仿皮草", "干洗或局部清洁") else en("Insulated hooded coat", "Winter commute/outdoor", "Knit + jeans/cargo", "Down/heavy fill", "Dry clean/spot clean")
            "Peacoat" -> if (isZh) zh("短款双排扣呢大衣", "冬季通勤", "高领针织 + 皮靴", "羊毛呢料", "干洗") else en("Short double-breasted coat", "Winter commute", "Turtleneck + boots", "Wool", "Dry clean")
            "Poncho" -> if (isZh) zh("披肩式外搭", "换季/拍照", "贴身内搭 + 紧身下装", "呢料/针织", "悬挂保存") else en("Cape-style layer", "Transitional seasons", "Fitted base + slim bottoms", "Wool/knit", "Hang store")
            "Sweater" -> if (isZh) zh("套头针织", "秋冬通勤与居家", "衬衫/打底 + 大衣/羽绒", "羊毛/羊绒/混纺", "平铺阴干") else en("Pullover knit", "Fall/winter daily", "Shirt/base + coat", "Wool/cashmere/blend", "Flat dry")
            "Tank" -> if (isZh) zh("背心", "夏季内搭/运动", "短裤/运动裤；外搭衬衫", "棉/速干", "与同色洗") else en("Tank top", "Summer base/sport", "Shorts/sweats; layer shirt", "Cotton/quick-dry", "Wash with similar colors")
            "Tee" -> if (isZh) zh("基础T恤", "日常通勤、出行、居家", "牛仔/休闲裤 + 运动鞋；外搭开衫/外套", "纯棉/棉氨；夏季速干", "低温烘干防缩水") else en("Basic tee", "Daily commute/home", "Jeans/chinos + sneakers; layer cardigan", "Cotton/spandex; quick-dry", "Low-temp tumble dry")
            "Top" -> if (isZh) zh("上衣统称", "日常通勤", "牛仔/半裙/西裤", "依面料而定", "遵循水洗标识") else en("Generic top", "Daily wear", "Jeans/skirt/trousers", "Fabric dependent", "Follow care label")
            "Turtleneck" -> if (isZh) zh("高领针织", "秋冬保暖内搭", "大衣/西装外套", "羊毛/混纺", "低温手洗/干洗") else en("High-neck knit", "Winter layering", "Coat/blazer", "Wool/blend", "Hand wash/dry clean")

            // Bottoms
            "Capris" -> if (isZh) zh("小腿长度修身/直筒", "春夏通勤休闲", "短外套/衬衫 + 乐福/运动鞋", "棉/弹力混纺", "低温机洗") else en("Calf-length slim/straight", "Spring/summer casual", "Short jacket/shirt + loafers/sneakers", "Cotton/stretch", "Cold gentle wash")
            "Chinos" -> if (isZh) zh("斜纹棉布休闲裤", "休闲商务/通勤", "T恤/衬衫/针织", "棉/弹力混纺", "反面洗，平整晾干") else en("Twill cotton pants", "Smart casual", "Tee/shirt/knit", "Cotton/stretch", "Wash inside-out")
            "Culottes" -> if (isZh) zh("阔腿短裤", "夏季通勤与出行", "修身上衣 + 凉鞋/乐福鞋", "轻薄梭织/针织", "轻柔机洗") else en("Wide-leg shorts", "Summer commute", "Fitted top + sandals/loafers", "Light woven/knit", "Gentle cycle")
            "Cutoffs" -> if (isZh) zh("毛边牛仔短裤", "夏季休闲度假", "T恤/背心 + 凉鞋/运动鞋", "丹宁棉", "反面洗，减少掉色") else en("Raw-hem denim shorts", "Summer casual", "Tee/tank + sandals/sneakers", "Denim cotton", "Inside-out wash")
            "Gauchos" -> if (isZh) zh("宽松七/八分阔腿裤", "通勤/休闲", "短上衣/修身针织 + 乐福", "垂感梭织/针织", "低温机洗，悬挂晾干") else en("Wide cropped trousers", "Work/casual", "Short top/fitted knit + loafers", "Drapey woven/knit", "Cold wash; hang dry")
            "Jeans" -> if (isZh) zh("直筒/锥形/阔腿/紧身", "通勤、休闲、旅行", "T恤/衬衫/毛衣 + 运动鞋/短靴", "四季皆宜，秋冬加厚", "反面冷水洗，降低频次") else en("Straight/tapered/wide/skinny", "Work, casual, travel", "Tee/shirt/sweater + sneakers/boots", "Year-round; heavier in winter", "Cold inside-out; wash less")
            "Jeggings" -> if (isZh) zh("紧身弹力丹宁打底", "日常出行/秋冬打底", "宽松上衣/外套 + 运动鞋", "高弹锦纶/棉混", "轻柔程序，避免拉扯") else en("Stretch denim leggings", "Daily/underlayer in winter", "Loose tops/jackets + sneakers", "High-stretch nylon/cotton", "Gentle cycle; avoid pulling")
            "Jodhpurs" -> if (isZh) zh("大腿宽松小腿收紧", "马术/时尚造型", "短靴 + 合身上装", "耐磨弹力面料", "遵循面料说明") else en("Loose thigh, tight calf pants", "Equestrian/fashion", "Ankle boots + fitted tops", "Durable stretch fabric", "Follow fabric care")
            "Joggers" -> if (isZh) zh("收口运动裤", "通勤休闲/轻运动", "卫衣/连帽衫 + 运动鞋", "棉/涤纶/抓绒", "反面洗，避免高温烘干") else en("Cuffed athletic pants", "Casual/athleisure", "Hoodie + sneakers", "Cotton/poly/fleece", "Inside-out; low heat")
            "Leggings" -> if (isZh) zh("瑜伽/运动打底", "健身训练、居家、秋冬打底", "宽松上衣 + 运动鞋", "锦纶氨纶混纺", "轻柔机洗，避免勾丝") else en("Yoga/athletic base", "Workout, home, winter layering", "Loose top + sneakers", "Nylon-spandex", "Gentle wash; avoid snagging")
            "Sarong" -> if (isZh) zh("裹身裙/沙滩巾", "海边度假、泳装外搭", "泳衣 + 凉鞋/拖鞋", "轻薄雪纺/人造丝", "轻手洗，避免扯裂") else en("Wrap skirt/beach scarf", "Beach vacation; swimsuit cover", "Swimsuit + sandals/slippers", "Light chiffon/rayon", "Hand wash gently")
            "Shorts" -> if (isZh) zh("常规短裤（休闲/工装）", "夏季外出与日常", "T恤/衬衫 + 凉鞋/运动鞋", "棉/麻/速干", "分色洗，避免高温烘干") else en("Casual/utility shorts", "Summer daily", "Tee/shirt + sandals/sneakers", "Cotton/linen/quick-dry", "Separate colors; low heat")
            "Skirt" -> if (isZh) zh("A字/直筒/百褶/包臀", "通勤与日常", "衬衫/针织/西装外套；长短靴/乐福鞋", "夏季雪纺，秋冬呢料/针织", "视材质选择机洗或干洗") else en("A-line/straight/pleated/pencil", "Work and daily", "Shirt/knit/blazer; boots/loafers", "Chiffon in summer, wool/knit in winter", "Machine or dry clean per fabric")
            "Sweatpants" -> if (isZh) zh("直筒/收口运动裤", "运动/休闲通勤", "卫衣/夹克 + 运动鞋", "棉/抓绒/涤纶", "反面洗，少柔顺剂") else en("Straight/jogger sweats", "Sport/casual", "Hoodie/jacket + sneakers", "Cotton/fleece/poly", "Inside-out; minimal softener")
            "Sweatshorts" -> if (isZh) zh("针织短裤", "夏季运动/居家", "运动T + 运动鞋/凉鞋", "棉/速干针织", "轻柔机洗") else en("Knit shorts", "Summer sport/home", "Athletic tee + sneakers/sandals", "Cotton/quick-dry knit", "Gentle cycle")
            "Trunks" -> if (isZh) zh("贴身或宽松泳裤", "游泳/海边度假", "速干上衣/沙滩巾", "速干面料/氯耐材质", "清水过洗，避曝晒") else en("Fitted or loose swim shorts", "Swimming/beach", "Quick-dry top/beach towel", "Chlorine-resistant quick-dry", "Rinse and avoid sun")

            // Robes / Dresses / One-piece / Traditional
            "Caftan", "Kaftan" -> if (isZh) zh("长袍式宽松连身", "度假/居家/礼拜", "凉鞋/拖鞋 + 简洁配饰", "轻薄棉麻/丝绸/针织", "手洗或干洗") else en("Loose robe-like dress", "Resort/home", "Sandals/simple accessories", "Light cotton/linen/silk", "Hand wash/dry clean")
            "Cape" -> if (isZh) zh("披肩型外搭", "换季造型/拍照", "简洁内搭 + 紧身下装", "呢料/针织", "悬挂存放，减少折痕") else en("Cape outer layer", "Transitional styling/photos", "Simple base + slim bottoms", "Wool/knit", "Hang to avoid creases")
            "Coat" -> if (isZh) zh("中长/长款大衣统称", "冬季通勤/出行", "针织/衬衫 + 西裤/牛仔 + 靴", "羊毛呢/混纺/羽绒内胆", "干洗") else en("Overcoat (mid/long)", "Winter commute", "Knit/shirt + trousers/jeans + boots", "Wool/blend/down lining", "Dry clean")
            "Coverup" -> if (isZh) zh("泳装外搭薄纱", "泳池/海边", "泳衣 + 凉鞋", "轻薄透气面料", "手洗平铺晾干") else en("Sheer swim cover", "Pool/beach", "Swimsuit + sandals", "Light breathable fabric", "Hand wash; flat dry")
            "Dress" -> if (isZh) zh("A字/直筒/收腰/礼服等", "约会、通勤、正式活动", "小西装/针织开衫 + 高跟/平底", "春夏雪纺/棉麻，秋冬针织/呢料", "精细面料手洗或干洗") else en("A-line/shift/fit&flare/gown", "Date, work, formal", "Blazer/cardigan + heels/flats", "Chiffon/linen (SS), knit/wool (FW)", "Delicate hand wash or dry clean")
            "Jumpsuit" -> if (isZh) zh("上下一体裤装", "通勤休闲/出街造型", "短外套/西装 + 乐福/高跟", "梭织/牛仔/针织", "分色洗，注意腰线长度") else en("One-piece pants outfit", "Smart casual/street", "Short jacket/blazer + loafers/heels", "Woven/denim/knit", "Wash by colors; mind torso length")
            "Kimono" -> if (isZh) zh("宽袖系带长袍式", "和风拍摄/室内/礼仪", "简约内搭 + 草履/拖鞋", "仿真丝/棉麻", "温和清洗，悬挂存放") else en("Wide-sleeve robe with sash", "Japanese-style shoots/home/ceremony", "Simple base + zori/slippers", "Satin-like/silk/linen", "Gentle wash; hang store")
            "Nightdress" -> if (isZh) zh("舒适宽松睡衣裙", "居家睡眠", "家居拖鞋 + 薄外开衫", "棉/莫代尔/真丝", "低温柔洗") else en("Comfortable sleep dress", "Home sleeping", "House slippers + light cardigan", "Cotton/modal/silk", "Low-temp delicate wash")
            "Onesie" -> if (isZh) zh("一体家居服/婴童连体", "居家休闲/保暖", "家居拖鞋", "绒毛/针织", "低温机洗，注意尺码") else en("One-piece loungewear/infant suit", "Home lounging/warmth", "House slippers", "Fleece/knit", "Cold machine wash; size carefully")
            "Robe" -> if (isZh) zh("系带式浴袍/家居袍", "沐浴后/居家", "家居拖鞋/室内穿着", "毛巾布/法兰绒/真丝", "充分晾干，保持蓬松") else en("Belted bath/home robe", "Post-shower/home", "House slippers", "Terry/fleece/silk", "Air dry fully; keep fluffy")
            "Romper" -> if (isZh) zh("上衣+短裤一体", "夏日出游/休闲", "凉鞋/运动鞋 + 轻便外搭", "棉/雪纺/牛仔", "轻柔机洗，避免变形") else en("Top+shorts one-piece", "Summer outings", "Sandals/sneakers + light layer", "Cotton/chiffon/denim", "Gentle wash; avoid distortion")
            "Shirtdress" -> if (isZh) zh("衬衫结构连衣裙", "通勤/休闲", "腰带强调比例 + 乐福/短靴", "棉/牛仔/混纺", "低温熨烫，防皱") else en("Shirt-structured dress", "Work/casual", "Belt for shape + loafers/ankle boots", "Cotton/denim/blend", "Low-temp iron")
            "Sundress" -> if (isZh) zh("细肩带/露肩夏日连衣裙", "度假/出游", "草编包+凉鞋/帆布鞋", "棉麻/雪纺/人造丝", "轻手洗，避免暴晒") else en("Strappy/off-shoulder summer dress", "Vacation/outings", "Straw bag + sandals/sneakers", "Cotton-linen/chiffon/rayon", "Hand wash; avoid sun")

            else -> if (isZh) zh("经典基础款", "日常与通勤", "与基础单品易组合", "按面料选择季节", "遵循水洗标识") else en("Classic essentials", "Daily & work", "Easy to pair with basics", "Season by fabric", "Follow care label")
        }
    }

    fun close() {
        ortSession?.close()
        ortSession = null
    }

    data class ClassificationResult(
        val category: String,
        val confidence: Float,
        val description: String
    )
}
