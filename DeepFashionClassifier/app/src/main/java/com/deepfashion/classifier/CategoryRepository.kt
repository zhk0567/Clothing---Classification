package com.deepfashion.classifier

enum class CategoryGroup {
    TOPS, BOTTOMS, ONE_PIECE
}

data class CategoryInfo(
    val english: String,
    val chinese: String,
    val group: CategoryGroup
)

object CategoryRepository {

    val allCategories: List<CategoryInfo> = listOf(
        CategoryInfo("Anorak", "冲锋衣", CategoryGroup.TOPS),
        CategoryInfo("Blazer", "西装外套", CategoryGroup.TOPS),
        CategoryInfo("Blouse", "女式衬衫", CategoryGroup.TOPS),
        CategoryInfo("Bomber", "飞行员夹克", CategoryGroup.TOPS),
        CategoryInfo("Button-Down", "纽扣衫", CategoryGroup.TOPS),
        CategoryInfo("Cardigan", "开衫", CategoryGroup.TOPS),
        CategoryInfo("Flannel", "法兰绒衬衫", CategoryGroup.TOPS),
        CategoryInfo("Halter", "挂脖上衣", CategoryGroup.TOPS),
        CategoryInfo("Henley", "半开领衫", CategoryGroup.TOPS),
        CategoryInfo("Hoodie", "连帽衫", CategoryGroup.TOPS),
        CategoryInfo("Jacket", "夹克", CategoryGroup.TOPS),
        CategoryInfo("Jersey", "运动衫", CategoryGroup.TOPS),
        CategoryInfo("Parka", "派克大衣", CategoryGroup.TOPS),
        CategoryInfo("Peacoat", "双排扣大衣", CategoryGroup.TOPS),
        CategoryInfo("Poncho", "斗篷", CategoryGroup.TOPS),
        CategoryInfo("Sweater", "毛衣", CategoryGroup.TOPS),
        CategoryInfo("Tank", "背心", CategoryGroup.TOPS),
        CategoryInfo("Tee", "T恤", CategoryGroup.TOPS),
        CategoryInfo("Top", "上衣", CategoryGroup.TOPS),
        CategoryInfo("Turtleneck", "高领衫", CategoryGroup.TOPS),
        CategoryInfo("Capris", "七分裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Chinos", "卡其裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Culottes", "阔腿短裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Cutoffs", "牛仔短裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Gauchos", "高乔裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Jeans", "牛仔裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Jeggings", "打底裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Jodhpurs", "马裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Joggers", "慢跑裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Leggings", "紧身裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Sarong", "纱笼", CategoryGroup.BOTTOMS),
        CategoryInfo("Shorts", "短裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Skirt", "裙子", CategoryGroup.BOTTOMS),
        CategoryInfo("Sweatpants", "运动裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Sweatshorts", "运动短裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Trunks", "泳裤", CategoryGroup.BOTTOMS),
        CategoryInfo("Caftan", "长袍", CategoryGroup.ONE_PIECE),
        CategoryInfo("Cape", "斗篷", CategoryGroup.ONE_PIECE),
        CategoryInfo("Coat", "外套", CategoryGroup.ONE_PIECE),
        CategoryInfo("Coverup", "罩衫", CategoryGroup.ONE_PIECE),
        CategoryInfo("Dress", "连衣裙", CategoryGroup.ONE_PIECE),
        CategoryInfo("Jumpsuit", "连体衣", CategoryGroup.ONE_PIECE),
        CategoryInfo("Kaftan", "长袍", CategoryGroup.ONE_PIECE),
        CategoryInfo("Kimono", "和服", CategoryGroup.ONE_PIECE),
        CategoryInfo("Nightdress", "睡裙", CategoryGroup.ONE_PIECE),
        CategoryInfo("Onesie", "连体服", CategoryGroup.ONE_PIECE),
        CategoryInfo("Robe", "长袍", CategoryGroup.ONE_PIECE),
        CategoryInfo("Romper", "连体短裤", CategoryGroup.ONE_PIECE),
        CategoryInfo("Shirtdress", "衬衫裙", CategoryGroup.ONE_PIECE),
        CategoryInfo("Sundress", "太阳裙", CategoryGroup.ONE_PIECE)
    )

    val englishNames: List<String> = allCategories.map { it.english }

    private val chineseMap: Map<String, String> = allCategories.associate { it.english to it.chinese }

    fun getDisplayName(english: String, isZh: Boolean): String {
        return if (isZh) chineseMap[english] ?: english else english
    }

    fun getEnglishName(displayOrEnglish: String): String? {
        if (englishNames.contains(displayOrEnglish)) return displayOrEnglish
        return allCategories.find { it.chinese == displayOrEnglish }?.english
    }

    fun getGrouped(): Map<CategoryGroup, List<CategoryInfo>> {
        return allCategories.groupBy { it.group }
    }

    fun search(query: String, isZh: Boolean): List<CategoryInfo> {
        if (query.isBlank()) return allCategories
        return allCategories.filter { info ->
            info.english.contains(query, ignoreCase = true) ||
                info.chinese.contains(query, ignoreCase = true)
        }
    }

    fun getDescription(categoryEn: String, isZh: Boolean): String {
        fun zh(style: String, scene: String, pair: String, season: String, care: String): String {
            return "风格: $style\n场景: $scene\n搭配: $pair\n季节材质: $season\n护理: $care"
        }
        fun en(style: String, scene: String, pair: String, season: String, care: String): String {
            return "Style: $style\nOccasions: $scene\nPairing: $pair\nSeason/Fabric: $season\nCare: $care"
        }
        return when (categoryEn) {
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

    fun getGroupLabel(group: CategoryGroup, isZh: Boolean): String {
        return when (group) {
            CategoryGroup.TOPS -> if (isZh) "上装" else "Tops"
            CategoryGroup.BOTTOMS -> if (isZh) "下装" else "Bottoms"
            CategoryGroup.ONE_PIECE -> if (isZh) "连身装" else "One-piece"
        }
    }
}
