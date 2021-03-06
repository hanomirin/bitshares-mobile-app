package com.btsplusplus.fowallet

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import bitshares.*
import com.btsplusplus.fowallet.kline.MKlineIndexMA
import com.btsplusplus.fowallet.kline.MKlineItemData
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

/**
 * KLine (K线图)
 */
class ViewKLine : ViewBase {

    enum class EKlineDatePeriodType(val value: Int) {
        ekdpt_timeline(1),     //  分时图
        ekdpt_1m(10),          //  1分
        ekdpt_5m(20),          //  5分
        ekdpt_15m(30),         //  15分
        ekdpt_30m(40),         //  30分
        ekdpt_1h(50),          //  1小时
        ekdpt_4h(60),          //  4小时
        ekdpt_1d(70),          //  日线
        ekdpt_1w(80),          //  周线
    }

    var crossView: ViewKLineCross? = null

    private var _first_init_draw: Boolean = true         //  首次初始化不描绘

    private var m_event_delegate: GestureDetector? = null
    private var m_scale_event_delegate: ScaleGestureDetector? = null

    var ekdptType: EKlineDatePeriodType? = null        //  K线周期类型
    var fOneCellHeight: Float = 0f                  //  主图（K线）区域一个CELL格高度
    var fMainGraphHeight: Float = 0f                //  主图（K线）区域总高度     该高度不包含 fMainMAHeight
    var fSecondGraphHeight: Float = 0f              //  副图（量）区域总高度      该高度不包含 fSecondMAHeight
    var fMainMAHeight: Float = 0f                   //  主图（K线）MA区域总高度
    var fSecondMAHeight: Float = 0f                 //  副图（量）MA区域总高度


    var _baseAsset: JSONObject? = null
    var _quoteAsset: JSONObject? = null
    var _base_precision: Int = 0
    var _quote_precision: Int = 0
    var _base_id: String? = null

    var _kdataModelPool: MutableList<MKlineItemData>? = null
    var _kdataModelCurrentIndex: Int = 0

    var _kdataArrayAll = mutableListOf<MKlineItemData>()           //  所有K线数据Model
    var _kdataArrayShowing = mutableListOf<MKlineItemData>()       //  当前屏幕显示中的数据Model
    var _currMaxPrice: BigDecimal? = null                   //  Y轴价格区间最高价格
    var _currMinPrice: BigDecimal? = null                  //  Y轴价格区间最低价格
    var _currRowPriceStep: BigDecimal? = null              //  每行价格阶梯
    var _currMaxVolume: BigDecimal? = null                 //  Vol区间当前最大交易量

    var _f10NumberSize: SizeF? = null                  //  测量字体高度

    var _fontname: Typeface? = null
    var _fontsize: Float? = null

    //  手势数据
    var long_press_down: Boolean = false                 //  长按手势是否经过了 down 事件
    var long_press: Boolean = false                     //  长按手势中
    var pan_gesture: Boolean = false                    //  拖拽手势中
    var _startTouch: PointF = PointF(0.0f, 0.0f)
    var _currCandleOffset: Int = 0
    var _panOffsetX: Float = 0.0f

    var _scaleStartPan: Float = 0.0f
    var _scale_gesture: Boolean = false


    //  缩放手势
    var _currCandleWidth: Int = 0                  //  当前蜡烛图宽度（0-9）
    var _currCandleTotalWidth: Int = 0               //  当前缩放蜡烛图总宽度（1-10）
    var _maxShowNumber: Int = 0                    //  当前屏幕最大显示蜡烛数量（根据蜡烛宽度动态计算）

    var _ma: MKlineIndexMA? = null

    var _items: ArrayList<MKlineItemData> = ArrayList()

    var init_scale: Float = 1.0f
    var scale_level: Float = 1.0f

    var draw_start_x: Float = 0f
    var draw_start_y: Float = 0f

    // 画笔
    var m_paint01 = Paint()
    var m_paint02 = Paint()
    var m_paint03 = Paint()
    var _context: Context? = null
    var _view_size: SizeF = SizeF(0f, 0f)
    var m_canvas: Canvas = Canvas()
    var first_refresh: Boolean = true
    var cross_candle_index: Int = 0

    lateinit var _kdata: JSONArray

    /**
     * dp 2 px
     */
    fun toDp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, this.resources.displayMetrics)
    }

    /**
     *  描绘文字 (ios为返回 Layer)
     */
    private fun getTextPaintWithString(text: String, textColor: Int, fontname: Typeface, fontsize: Float): Paint {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textSize = fontsize
        paint.color = textColor
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = fontname
        return paint
    }

    /**
     *  描绘蜡烛图和影线
     */
    private fun genCandleLayer(model: MKlineItemData, index: Int, candle_width: Float) {

        //  蜡烛宽度（包括中间间隔像素）
        val spaceW = candle_width * 2 + kBTS_KLINE_SHADOW_WIDTH + kBTS_KLINE_INTERVAL

        //  判断涨跌来设置颜色
        var candle_paint: Paint? = null
        if (model.isRise) {
            candle_paint = m_paint02
        } else {
            candle_paint = m_paint03
        }
        candle_paint.strokeWidth = 1.0f.dp
        val path = Path()

        //  绘制蜡烛和上下影线（如果candle_width为0了则只描绘影线，不描绘蜡烛。）
        if (candle_width > 0) {
            var fHeight: Float = 0f
            var yOffset: Float = 0f
            if (model.isRise) {
                yOffset = floor(model.fOffsetClose)
                fHeight = max(Math.abs(ceil(model.fOffsetOpen.minus(model.fOffsetClose))), 1.0f.dp)
            } else {
                yOffset = floor(model.fOffsetOpen)
                fHeight = max(Math.abs(ceil(model.fOffsetClose.minus(model.fOffsetOpen))), 1.0f.dp)
            }

            var x1 = index * spaceW
            var y1 = fMainMAHeight + yOffset
            var x2 = x1 + candle_width * 2 + kBTS_KLINE_SHADOW_WIDTH
            var y2 = y1 + fHeight

            val candleFrame: RectF = RectF(x1, y1, x2, y2)
            path.addRect(candleFrame, Path.Direction.CW)
            m_canvas.drawPath(path, candle_paint)
        }

        val x1 = index * spaceW + candle_width
        val y1 = fMainMAHeight.plus(floor(model.fOffsetHigh))
        val x2 = index * spaceW + candle_width
        val y2 = fMainMAHeight.plus(ceil(model.fOffsetLow))
        m_canvas.drawLine(x1, y1, x2, y2, candle_paint)
    }


    /**
     *  绘制成交量柱子
     */
    private fun genVolumeLayer(model: MKlineItemData, index: Int, candle_width: Float) {
        val spaceW: Float = candle_width * 2 + kBTS_KLINE_SHADOW_WIDTH + kBTS_KLINE_INTERVAL

        //  判断涨跌来设置颜色
        val candle_paint: Paint
        if (model.isRise) {
            candle_paint = m_paint02
        } else {
            candle_paint = m_paint03
        }

        //  REMARK：从最底部倒着往上绘制，高度设置为负数
        if (candle_width > 0) {
            val x1 = index * spaceW
            val y2 = _view_size.height
            val x2 = x1 + candle_width * 2 + kBTS_KLINE_SHADOW_WIDTH
            val y1 = y2 + ceil(-model.fOffset24Vol)

            val candleFrame = RectF(x1, y1, x2, y2)
            m_canvas.drawRect(candleFrame, candle_paint)
        } else {
            m_canvas.drawLine(
                    index * spaceW,
                    _view_size.height,
                    index * spaceW,
                    floor(_view_size.height - model.fOffset24Vol), candle_paint)
        }
    }


    /**
     *  (private) 初始化背景边框图层
     */
    private fun genBackFrameLayer(frame: RectF) {

        val frameX: Float = 0f
        val frameY: Float = 0f
        val frameW: Float = frame.width()
        val frameH: Float = frame.height()

        //  初始化一个路径
        val framePath = Path()
        val paint: Paint = m_paint01
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = toDp(1.0f)
        framePath.addRect(frame, Path.Direction.CW)


        val cellW: Float = frameW / kBTS_KLINE_COL_NUM
        val cellH: Float = frameH / kBTS_KLINE_ROW_NUM

        //  绘制竖线（kBTS_KLINE_COL_NUM - 1）条
        for (i in 0 until kBTS_KLINE_COL_NUM - 1) {
            framePath.moveTo(frameX + cellW * (i + 1), frameY)
            framePath.lineTo(frameX + cellW * (i + 1), frameY + frameH)
        }

        //  绘制横线（kBTS_KLINE_ROW_NUM - 1）条。由于区域顶部显示MA指标，所以横线需要往下偏移。
        val fMAOffset = kBTS_KLINE_MA_HEIGHT.toFloat() * cellH
        for (i in 0 until kBTS_KLINE_ROW_NUM - 1) {
            framePath.moveTo(frameX, frameY + cellH * (i + 1) + fMAOffset)
            framePath.lineTo(frameX + frameW, frameY + cellH * (i + 1) + fMAOffset)
        }
        m_canvas.drawPath(framePath, paint)
    }


    /**
     *  (private) 计算屏幕宽度一次可以显示的蜡烛数量，蜡烛可缩放。
     *  candle_width - 3、2、1、0（最小宽度为0，则蜡烛和影线一样了、没实体）
     */
    private fun calcMaxShowCandleNumber(candle_width: Float): Int {
        val fMaxWidth: Float = _view_size.width
        val fWidthCandle: Float = candle_width * 2 + kBTS_KLINE_SHADOW_WIDTH
        val fRealWidth: Float = fWidthCandle + kBTS_KLINE_INTERVAL

        var num: Int = floor(fMaxWidth / fRealWidth).toInt()

        //  余下的宽度虽然没有 9，但如果宽度有7，显示数量应该+1。最后一根蜡烛不包含间距。
        val mod: Float = fMaxWidth - num * fRealWidth
        if (mod >= fWidthCandle) {
            num += 1
        }
        return num
    }

    private fun getOneKdataModel(): MKlineItemData {
        if (_kdataModelCurrentIndex >= _kdataModelPool!!.count()) {
            _kdataModelPool!!.add(MKlineItemData())
        }
        val m = _kdataModelPool!!.get(_kdataModelCurrentIndex)
        _kdataModelCurrentIndex++
        m.reset()
        return m
    }

    private fun prepareAllDatas(data_array: JSONArray) {
        _kdataArrayAll.clear()

        //  重制！！！重要。
        _kdataModelCurrentIndex = 0

        //  无数据
        if (data_array.length() <= 0) {
            return
        }

        //  保留小数位数 向上取整
        val ceilHandler: Array<Int> = arrayOf(_base_precision, BigDecimal.ROUND_UP)
        val percentHandler: Array<Int> = arrayOf(4, BigDecimal.ROUND_UP)

        //  MA(n)辅助计算类
        var ma5 = MKlineIndexMA(5, _kdataArrayAll, ceilHandler) {
            it.nPriceClose!!
        }

        var ma10 = MKlineIndexMA(10, _kdataArrayAll, ceilHandler) {
            it.nPriceClose!!
        }

        var ma30 = MKlineIndexMA(30, _kdataArrayAll, ceilHandler) {
            it.nPriceClose!!
        }

        //  REMARK：目前仅分时图才显示MA60
        var ma60: MKlineIndexMA? = null
        if (isDrawTimeLine()) {
            ma60 = MKlineIndexMA(60, _kdataArrayAll, ceilHandler) {
                it.nPriceClose!!
            }
        }

        var vol_ma5 = MKlineIndexMA(5, _kdataArrayAll, ceilHandler) {
            it.n24Vol!!
        }

        var vol_ma10 = MKlineIndexMA(10, _kdataArrayAll, ceilHandler) {
            it.n24Vol!!
        }

        data_array.forEach<JSONObject> { data: JSONObject? ->
            //  创建Model
            var model: MKlineItemData = getOneKdataModel()

            //  解析Model
            model = MKlineItemData.parseData(data!!, model, _base_id, _base_precision, _quote_precision, ceilHandler, percentHandler)
            _kdataArrayAll.add(model)

            //  计算MA（n）
            model.ma5 = ma5.calc_ma(model)
            model.ma10 = ma10.calc_ma(model)
            model.ma30 = ma30.calc_ma(model)
            if (ma60 != null) {
                model.ma60 = ma60.calc_ma(model)
            }
            model.vol_ma5 = vol_ma5.calc_ma(model)
            model.vol_ma10 = vol_ma10.calc_ma(model)
        }
    }

    /**
     *  (private) 准备所有显示用model（每次刷新都需要重新计算最高点、最低点、蜡烛图坐标等数据）
     *  candle_offset   - 右边跳过数据数量
     */
    private fun prepareShowData(maxShowNum: Int, candle_offset: Int) {
        //  根据屏幕宽度指定的显示数据获取需要显示的数据  REMARK：data_array[最旧....最新]
        _kdataArrayShowing.clear()
        val n_data_array: Int = _kdataArrayAll.count()

        //  无数据
        if (n_data_array <= 0) {
            return
        }

        var enumIndexOffset: Int = n_data_array - maxShowNum - candle_offset
        if (enumIndexOffset < 0) {
            enumIndexOffset = 0
        }

        for (enumIndex in enumIndexOffset until n_data_array) {
            var m: MKlineItemData = _kdataArrayAll.get(enumIndex)
            m.isMax24Vol = false
            m.isMaxPrice = false
            m.isMinPrice = false
            _kdataArrayShowing.add(m)

            if (_kdataArrayShowing.count() >= maxShowNum) {
                break
            }
        }

        //  分时图
        val onlyTimeLine: Boolean = isDrawTimeLine()

        //  寻找最大价格、最小价格、最大成交量（REMARK：最大最小价格区域包含MA的价格
        var first_data: MKlineItemData = _kdataArrayShowing.first()
        var price_max_item = first_data
        var price_min_item = first_data
        var volume_max_item = first_data

        //  全部数据的最大价格：包括蜡烛、影线和移动均线（整个绘制区域包含均线，所以需要考虑均线价格因素。）
        var max_price = if (onlyTimeLine) {
            first_data.nPriceClose
        } else {
            first_data.nPriceHigh
        }
        var min_price = if (onlyTimeLine) {
            first_data.nPriceClose
        } else {
            first_data.nPriceLow
        }

        //  所有蜡烛（包括影线）的最大价格和最小价格
        var candle_max_price_item = first_data
        var candle_min_price_item = first_data
        var candle_max_price = first_data.nPriceHigh
        var candle_min_price = first_data.nPriceLow
        var max_24vol = first_data.n24Vol

        var h: BigDecimal? = null
        var l: BigDecimal? = null
        var c: BigDecimal? = null
        var vol: BigDecimal? = null
        var ma5: BigDecimal? = null
        var ma10: BigDecimal? = null
        var ma30: BigDecimal? = null
        var ma60: BigDecimal? = null
        var vol_ma5: BigDecimal? = null
        var vol_ma10: BigDecimal? = null

        val NSOrderedSame = 0
        val NSOrderedDescending = 1
        val NSOrderedAscending = -1

        for (m in _kdataArrayShowing) {
            if (onlyTimeLine) {
                //  统计分时Y轴最高最低价格用
                c = m.nPriceClose
                ma60 = m.ma60
            } else {
                //  统计K线Y轴最高最低价格、以及蜡烛图最高最低价格（由于MA存在两者可能不同）
                h = m.nPriceHigh
                l = m.nPriceLow
                ma5 = m.ma5
                ma10 = m.ma10
                ma30 = m.ma30
            }

            //  统计副图Y轴最大交易量
            vol = m.n24Vol
            vol_ma5 = m.vol_ma5
            vol_ma10 = m.vol_ma10

            if (onlyTimeLine) {
                //  分时
                //  最高价格
                if (c!!.compareTo(max_price) == NSOrderedDescending) {
                    max_price = c
                    price_max_item = m
                }
                if (ma60 != null && ma60.compareTo(max_price) == NSOrderedDescending) {
                    max_price = ma60
                    price_max_item = m
                }
                if (c.compareTo(min_price) == NSOrderedAscending) {
                    min_price = c
                    price_min_item = m
                }
                if (ma60 != null && ma60.compareTo(min_price) == NSOrderedAscending) {
                    min_price = ma60
                    price_min_item = m
                }
            } else {

                //  K线
                //  h > candle_max_price
                if (h!!.compareTo(candle_max_price) == NSOrderedDescending) {
                    candle_max_price = h
                    candle_max_price_item = m
                }
                //  h > max_price
                if (h.compareTo(max_price) == NSOrderedDescending) {
                    max_price = h
                    price_max_item = m
                }
                if (ma5 != null && ma5.compareTo(max_price) == NSOrderedDescending) {
                    max_price = ma5
                    price_max_item = m
                }
                if (ma10 != null && ma10.compareTo(max_price) == NSOrderedDescending) {
                    max_price = ma10
                    price_max_item = m
                }
                if (ma30 != null && ma30.compareTo(max_price) == NSOrderedDescending) {
                    max_price = ma30
                    price_max_item = m
                }
                //  l < candle_min_price
                if (l!!.compareTo(candle_min_price) == NSOrderedAscending) {
                    candle_min_price = l
                    candle_min_price_item = m
                }
                //  l < min_price
                if (l.compareTo(min_price) == NSOrderedAscending) {
                    min_price = l
                    price_min_item = m
                }
                if (ma5 != null && ma5.compareTo(min_price) == NSOrderedAscending) {
                    min_price = ma5
                    price_min_item = m
                }
                if (ma10 != null && ma10.compareTo(min_price) == NSOrderedAscending) {
                    min_price = ma10
                    price_min_item = m
                }
                if (ma30 != null && ma30.compareTo(min_price) == NSOrderedAscending) {
                    min_price = ma30
                    price_min_item = m
                }
            }

            //  vol > max_24vol
            if (vol!!.compareTo(max_24vol) == NSOrderedDescending) {
                max_24vol = vol
                volume_max_item = m
            }
            if (vol_ma5 != null && vol_ma5.compareTo(max_24vol) == NSOrderedDescending) {
                max_24vol = vol_ma5
                volume_max_item = m
            }
            if (vol_ma10 != null && vol_ma10.compareTo(max_24vol) == NSOrderedDescending) {
                max_24vol = vol_ma10
                volume_max_item = m
            }
        }

        candle_max_price_item.isMaxPrice = true
        candle_min_price_item.isMinPrice = true
        volume_max_item.isMax24Vol = true

        //  REMARK：特殊情况，如果最大最小值为0，那么在屏幕上就只有一个点，不存在区间，那么Y轴价格区间就没法显示，这种情况价格区间上下浮动 10%。
        if (max_price!!.compareTo(min_price) == NSOrderedSame) {
            //  max_price *= 1.1;
            //  min_price *= 0.9;
            val ceilHanderScale = _base_precision
            val ceilHanderScaleRounding = BigDecimal.ROUND_UP
            val n_percent_90 = BigDecimal("0.9")
            val n_percent_110 = BigDecimal("1.1")
            max_price = max_price.multiply(n_percent_110).setScale(ceilHanderScale, ceilHanderScaleRounding)
            min_price = min_price!!.multiply(n_percent_90).setScale(ceilHanderScale, ceilHanderScaleRounding)
        }

        //  记住Y轴价格区间最高、最低价格、并计算差价等。
        _currMaxPrice = max_price
        _currMinPrice = min_price
        _currMaxVolume = max_24vol

        val currDiffPrice: BigDecimal = max_price!!.subtract(min_price)
        val f_diff_price: Double = currDiffPrice.toDouble()
        assert(kBTS_KLINE_ROW_NUM >= 2)

        val n_rows: BigDecimal = BigDecimal(kBTS_KLINE_ROW_NUM - 1).setScale(0)
        _currRowPriceStep = currDiffPrice.divide(n_rows)

        //  计算开收高低屏幕位置 REMARK：K线可描绘区域底部流程半个行高，用于显示MIN价格，不然MIN价格会超出底线。
        val fViewMaxHeight: Float = (fMainGraphHeight - ceil(_f10NumberSize!!.height / 2.0)).toFloat()
        val fSecondViewHeight = fSecondGraphHeight

        for (m in _kdataArrayShowing) {
            m.fOffsetOpen = (max_price.subtract(m.nPriceOpen).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            m.fOffsetClose = (max_price.subtract(m.nPriceClose).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            m.fOffsetHigh = (max_price.subtract(m.nPriceHigh).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            m.fOffsetLow = (max_price.subtract(m.nPriceLow).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            m.fOffset24Vol = ((m.n24Vol!!.divide(max_24vol, 8, BigDecimal.ROUND_UP)).toDouble() * (fSecondViewHeight.toDouble())).toFloat()

            if (m.ma5 != null) {
                m.fOffsetMA5 = (max_price.subtract(m.ma5).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            }
            if (m.ma10 != null) {
                m.fOffsetMA10 = (max_price.subtract(m.ma10).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            }
            if (m.ma30 != null) {
                m.fOffsetMA30 = (max_price.subtract(m.ma30).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            }
            if (m.ma60 != null) {
                m.fOffsetMA60 = (max_price.subtract(m.ma60).toDouble() * fViewMaxHeight / f_diff_price).toFloat()
            }
            if (m.vol_ma5 != null) {
                m.fOffsetVolMA5 = (m.vol_ma5!!.divide(max_24vol, 8, BigDecimal.ROUND_UP).toDouble() * fSecondViewHeight).toFloat()
            }
            if (m.vol_ma10 != null) {
                m.fOffsetVolMA10 = (m.vol_ma10!!.divide(max_24vol, 8, BigDecimal.ROUND_UP).toDouble() * fSecondViewHeight).toFloat()
            }
        }
    }

    /**
     *  (private) 生成单条线
     */
    private fun getSingleLineLayerWithPointArray(pointArr: MutableList<PointF>, lineColor: Int) {
        val path = Path()
        val first_point = pointArr.first()
        path.moveTo(first_point.x, first_point.y)
        var point: PointF? = null
        for (idxY in 1 until pointArr.size) {
            point = pointArr.get(idxY)
            path.lineTo(point.x, point.y)
        }
        val paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = lineColor
        paint.strokeWidth = toDp(1.0f)
        m_canvas.drawPath(path, paint)
    }

    /**
     * 格式化日期
     */
    fun formatDateString(date_ts: Long): String {
        val d = Date(date_ts * 1000)
        //  REMARK：BTS默认时间是UTC时间，这里按照本地时区格式化。
        var fmt = ""
        when (ekdptType!!.value) {
            ViewKLine.EKlineDatePeriodType.ekdpt_timeline.value -> fmt = "HH:mm"
            ViewKLine.EKlineDatePeriodType.ekdpt_1m.value -> fmt = "HH:mm"
            ViewKLine.EKlineDatePeriodType.ekdpt_5m.value -> fmt = "MM-dd HH:mm"
            ViewKLine.EKlineDatePeriodType.ekdpt_15m.value -> fmt = "MM-dd HH:mm"
            ViewKLine.EKlineDatePeriodType.ekdpt_30m.value -> fmt = "MM-dd HH:mm"
            ViewKLine.EKlineDatePeriodType.ekdpt_1h.value -> fmt = "MM-dd HH:mm"
            ViewKLine.EKlineDatePeriodType.ekdpt_4h.value -> fmt = "MM-dd HH:mm"
            ViewKLine.EKlineDatePeriodType.ekdpt_1d.value -> fmt = "yy-MM-dd"
            ViewKLine.EKlineDatePeriodType.ekdpt_1w.value -> fmt = "yy-MM-dd"
            else -> return "--"
        }
        return SimpleDateFormat(fmt).format(d)
    }

    /**
     *  当前是否显示分时图
     */
    fun isDrawTimeLine(): Boolean {
        return ekdptType == EKlineDatePeriodType.ekdpt_timeline
    }

    /**
     *  描绘分时图
     */
    private fun drawTimeLine(candle_width: Float) {
        val candleSpaceW: Float = candle_width * 2 + kBTS_KLINE_SHADOW_WIDTH + kBTS_KLINE_INTERVAL

        val timeline_points: MutableList<PointF> = mutableListOf()
        for ((idx, m: MKlineItemData) in _kdataArrayShowing.withIndex()) {
            var yOffset: Float? = null
            yOffset = if (m.isRise) {
                fMainMAHeight.plus(floor(m.fOffsetClose))
            } else {
                fMainMAHeight.plus(ceil(m.fOffsetClose))
            }
            timeline_points.add(PointF(idx * candleSpaceW + candle_width, yOffset))
        }

        if (timeline_points.count() >= 2) {
            //  1、分时图线
            val path = Path()
            var point: PointF = timeline_points.first()
            path.moveTo(point.x, point.y)
            for (idxY in 1 until timeline_points.count() - 1) {
                point = timeline_points.get(idxY)
                path.lineTo(point.x, point.y)
            }
            // path.close()
            val paint = Paint()

            paint.style = Paint.Style.STROKE
            paint.shader = null
            paint.color = resources.getColor(R.color.theme01_textColorHighlight)
            paint.flags = Paint.ANTI_ALIAS_FLAG
            paint.strokeWidth = 4.0f//TODO:

            m_canvas.drawPath(path, paint)

            //  2.1、分时下面封闭区域渐变背景mask
            val firstPoint = timeline_points.first()
            val lastPoint = timeline_points.last()
            val maxOffsetY = fMainMAHeight + fMainGraphHeight

            //  连接成封闭图形，才可以填充颜色。（连线顺序顺时针）
            val maskPath = path
            maskPath.lineTo(lastPoint.x, maxOffsetY)
            maskPath.lineTo(firstPoint.x, maxOffsetY)
            maskPath.lineTo(firstPoint.x, firstPoint.y)
            maskPath.close()

            //  TODO:color
            val lg = LinearGradient(0f, 0f, 0f, 400f, resources.getColor(R.color.theme01_color03), resources.getColor(R.color.theme01_color01), Shader.TileMode.CLAMP)
            paint.strokeWidth = 1.0f
            paint.color = resources.getColor(R.color.theme01_color03)
            paint.style = Paint.Style.FILL

            paint.shader = lg
            m_canvas.drawPath(maskPath, paint)
        }
    }

    /**
     * 描绘核心
     */
    private fun drawCore(maxShowNum: Int, candle_width: Float) {
        //  无数据
        if (_kdataArrayShowing.count() <= 0) {
            val viewSize: SizeF = _view_size
            val str: String = resources.getString(R.string.klineNodData)
            val fontsize: Float = 30f.dp
            val text_paint: Paint = getTextPaintWithString(str, resources.getColor(R.color.theme01_textColorGray), Typeface.DEFAULT, fontsize)
            val frame = RectF(0f, 0f, viewSize.width, viewSize.height)
            m_canvas.drawText(str, frame.centerX(), frame.centerY(), text_paint)
            return
        }

        val candleSpaceW: Float = candle_width * 2 + kBTS_KLINE_SHADOW_WIDTH + kBTS_KLINE_INTERVAL

        //  保留小数位数 向上取整
        val ceilHandlerScale: Int = _base_precision
        val ceilHandlerRounding: Int = BigDecimal.ROUND_UP

        //  1、描绘背景右边(Y轴)价格区间
        var currStep = BigDecimal(_currMinPrice!!.toPlainString())

        for (i in 0 until kBTS_KLINE_ROW_NUM) {
            var txtOffsetY: Float? = null
            var price: BigDecimal? = null
            when (i) {
                0 -> {
                    price = _currMinPrice
                    txtOffsetY = fMainGraphHeight.plus(fMainMAHeight) - _f10NumberSize!!.height + 8.0f
                }
                kBTS_KLINE_ROW_NUM - 1 -> {
                    price = _currMaxPrice
                    txtOffsetY = _f10NumberSize!!.height * 2
                }
                else -> {
                    price = currStep.add(_currRowPriceStep).setScale(ceilHandlerScale, ceilHandlerRounding)
                    currStep = price
                    txtOffsetY = fMainGraphHeight.plus(fMainMAHeight) - _f10NumberSize!!.height - fOneCellHeight.times(i) + 8.0f
                }
            }
            val str: String = price.toString()
            var txt_paint = getTextPaintWithString(str, resources.getColor(R.color.theme01_textColorNormal), _fontname!!, _fontsize!!)
            txt_paint.textAlign = Paint.Align.RIGHT
            val frame = RectF(0f, txtOffsetY, _view_size.width - 4, _f10NumberSize!!.height)
            m_canvas.drawText(str, frame.right, frame.top, txt_paint)
        }

        //  2、描绘底部x轴时间
        for (i in 0..kBTS_KLINE_COL_NUM) {
            var dateCandleIndex: Int? = null
            var align: Any? = null
            var txtX: Float? = null
            when (i) {
                0 -> {
                    dateCandleIndex = 0
                    align = Paint.Align.LEFT
                    txtX = 2.0f.dp
                }
                kBTS_KLINE_COL_NUM -> {
                    dateCandleIndex = maxShowNum - 1
                    align = Paint.Align.RIGHT
                    txtX = _view_size.width - 2.dp
                }
                else -> {
                    dateCandleIndex = round(i.times(maxShowNum).div(kBTS_KLINE_COL_NUM).toDouble()).toInt()
                    align = Paint.Align.CENTER
                    txtX = i * _view_size.width / kBTS_KLINE_COL_NUM
                }
            }
            //  有可能时间轴区域对上去没有蜡烛信息（比如刚开盘或者成交量低等交易对）。所以需要用 safe 接口获取数据。
            val m: MKlineItemData? = _kdataArrayShowing.getOrNull(dateCandleIndex)
            if (m != null) {
                val str: String = formatDateString(m.date)
                val txt_paint: Paint = getTextPaintWithString(str, resources.getColor(R.color.theme01_textColorNormal), _fontname!!, _fontsize!!)
                val offsetY = _view_size.height + 4f.dp + kBTS_KLINE_PRICE_VOL_FONTSIZE
                txt_paint.textAlign = align
                m_canvas.drawText(str, txtX, offsetY, txt_paint)
            }
        }

        //  3、描绘中间主区域蜡烛图影线和成交量
        var candle_max_price_model: MKlineItemData? = null
        var candle_min_price_model: MKlineItemData? = null
        var idx = 0
        for (m: MKlineItemData in _kdataArrayShowing) {
            m.showIndex = idx

            //  非分时图的情况下描绘蜡烛图
            if (!isDrawTimeLine()) {
                genCandleLayer(m, idx, candle_width)
            }

            //  描绘成交量
            genVolumeLayer(m, idx, candle_width)

            //  分时图不显示最高、最低价格指标
            if (!isDrawTimeLine()) {
                if (m.isMaxPrice) {
                    candle_max_price_model = m
                }
                if (m.isMinPrice) {
                    candle_min_price_model = m
                }
            }
            ++idx
        }

        //  描绘分时图
        if (isDrawTimeLine()) {
            drawTimeLine(candle_width)
        }

        //  4、描绘MA均线（覆盖在蜡烛图上面）
        var ma5_points: MutableList<PointF> = mutableListOf()
        var ma10_points: MutableList<PointF> = mutableListOf()
        var ma30_points: MutableList<PointF> = mutableListOf()
        var ma60_points: MutableList<PointF> = mutableListOf()
        var vol_ma5_points: MutableList<PointF> = mutableListOf()
        var vol_ma10_points: MutableList<PointF> = mutableListOf()

        for ((idx: Int, m: MKlineItemData) in _kdataArrayShowing.withIndex()) {
            //  分时和蜡烛图分别描绘不同移动均线
            if (isDrawTimeLine()) {
                if (m.ma60 != null) {
                    ma60_points.add(PointF(idx * candleSpaceW + candle_width, m.fOffsetMA60 + fMainMAHeight))
                }
            } else {
                if (m.ma5 != null) {
                    ma5_points.add(PointF(idx * candleSpaceW + candle_width, m.fOffsetMA5 + fMainMAHeight))
                }
                if (m.ma10 != null) {
                    ma10_points.add(PointF(idx * candleSpaceW + candle_width, m.fOffsetMA10 + fMainMAHeight))
                }
                if (m.ma30 != null) {
                    ma30_points.add(PointF(idx * candleSpaceW + candle_width, m.fOffsetMA30 + fMainMAHeight))
                }
            }
            //  成交量移动均线描绘
            if (m.vol_ma5 != null) {
                vol_ma5_points.add(PointF(idx * candleSpaceW + candle_width, floor(_view_size.height - m.fOffsetVolMA5)))
            }
            if (m.vol_ma10 != null) {
                vol_ma10_points.add(PointF(idx * candleSpaceW + candle_width, floor(_view_size.height - m.fOffsetVolMA10)))
            }
        }

        if (ma5_points.count() >= 2) {
            getSingleLineLayerWithPointArray(ma5_points, resources.getColor(R.color.theme01_ma5Color))
        }
        if (ma10_points.count() >= 2) {
            getSingleLineLayerWithPointArray(ma10_points, resources.getColor(R.color.theme01_ma10Color))
        }
        if (ma30_points.count() >= 2) {
            getSingleLineLayerWithPointArray(ma30_points, resources.getColor(R.color.theme01_ma30Color))
        }
        if (ma60_points.count() >= 2) {
            //  同MA5颜色
            getSingleLineLayerWithPointArray(ma60_points, resources.getColor(R.color.theme01_ma5Color))
        }
        if (vol_ma5_points.count() >= 2) {
            getSingleLineLayerWithPointArray(vol_ma5_points, resources.getColor(R.color.theme01_ma5Color))
        }
        if (vol_ma10_points.count() >= 2) {
            getSingleLineLayerWithPointArray(vol_ma10_points, resources.getColor(R.color.theme01_ma10Color))
        }

        //  5、描绘副图最大成交量、主图最大价格、最小价格
        if (_currMaxVolume != null) {
            val txtPaint: Paint = getTextPaintWithString(_currMaxVolume!!.toPlainString(), resources.getColor(R.color.theme01_textColorNormal), _fontname!!, _fontsize!!)
            val frame: RectF = RectF(0f, fMainGraphHeight.plus(fMainMAHeight) + kBTS_KLINE_PRICE_VOL_FONTSIZE, _view_size.width - 4.dp, fSecondMAHeight)
            txtPaint.textAlign = Paint.Align.RIGHT
            m_canvas.drawText(_currMaxVolume!!.toPlainString(), frame.right, frame.top, txtPaint)
        }

        if (candle_max_price_model != null) {

            val str: String = candle_max_price_model.nPriceHigh!!.toPlainString()
            val str_size: Size = auxSizeWithText(str, _fontname!!, _fontsize!!)
            val txtOffsetY: Float = fMainMAHeight.plus(floor(candle_max_price_model.fOffsetHigh))

            var txtOffsetX: Float? = null
            var lineStartX: Float? = null
            var lineEndX: Float? = null
            var lineY: Float = fMainMAHeight + floor(candle_max_price_model.fOffsetHigh)

            if (candle_max_price_model.showIndex >= maxShowNum / 2) {
                //  最高价格在右边区域：靠左边显示
                lineStartX = candle_max_price_model.showIndex * candleSpaceW + candle_width - kBTS_KLINE_HL_PRICE_SHORT_LINE_LENGTH
                lineEndX = lineStartX + kBTS_KLINE_HL_PRICE_SHORT_LINE_LENGTH
                txtOffsetX = lineStartX - 2 - str_size.width
            } else {
                //  最高价格在右边区域：靠右边显示
                lineStartX = candle_max_price_model.showIndex * candleSpaceW + candle_width
                lineEndX = lineStartX + kBTS_KLINE_HL_PRICE_SHORT_LINE_LENGTH
                txtOffsetX = lineEndX + 2
            }

            val txt_paint: Paint = getTextPaintWithString(str, resources.getColor(R.color.theme01_textColorMain), _fontname!!, _fontsize!!)
            val frame: RectF = RectF(txtOffsetX, txtOffsetY, txtOffsetX + str_size.width.toFloat(), txtOffsetY + str_size.height.toFloat())
            m_canvas.drawText(str, frame.centerX(), frame.centerY(), txt_paint)

            //  短横线-指向最高价格
            val paint = Paint()
            val framePath = Path()
            val startPoint = PointF(lineStartX, lineY)
            val endPoint = PointF(lineEndX, lineY)
            framePath.moveTo(startPoint.x, startPoint.y)
            framePath.lineTo(endPoint.x, endPoint.y)
            paint.style = Paint.Style.STROKE
            paint.color = resources.getColor(R.color.theme01_textColorMain)
            paint.strokeWidth = 1.0f.dp
            m_canvas.drawPath(framePath, paint)
        }

        if (candle_min_price_model != null) {
            val str: String = candle_min_price_model.nPriceLow!!.toPlainString()
            val str_size: Size = auxSizeWithText(str, _fontname!!, _fontsize!!)
            val txtOffsetY: Float = fMainMAHeight + ceil(candle_min_price_model.fOffsetLow)

            var txtOffsetX: Float? = null
            var lineStartX: Float? = null
            var lineEndX: Float? = null
            var lineY: Float = fMainMAHeight + ceil(candle_min_price_model.fOffsetLow)

            if (candle_min_price_model.showIndex >= maxShowNum / 2) {
                //  最低价格在右边区域：靠左边显示
                lineStartX = candle_min_price_model.showIndex * candleSpaceW + candle_width - kBTS_KLINE_HL_PRICE_SHORT_LINE_LENGTH
                lineEndX = lineStartX + kBTS_KLINE_HL_PRICE_SHORT_LINE_LENGTH
                txtOffsetX = lineStartX - 2 - str_size.width
            } else {
                //  最低价格在右边区域：靠右边显示
                lineStartX = candle_min_price_model.showIndex * candleSpaceW + candle_width
                lineEndX = lineStartX + kBTS_KLINE_HL_PRICE_SHORT_LINE_LENGTH
                txtOffsetX = lineEndX + 2
            }

            val txt_paint: Paint = getTextPaintWithString(str, resources.getColor(R.color.theme01_textColorMain), _fontname!!, _fontsize!!)
            val frame: RectF = RectF(txtOffsetX, txtOffsetY, txtOffsetX + str_size.width.toFloat(), txtOffsetY + str_size.height.toFloat())
            m_canvas.drawText(str, frame.centerX(), frame.centerY(), txt_paint)

            //  短横线-指向最高价格
            val paint = Paint()
            val framePath = Path()
            val startPoint = PointF(lineStartX, lineY)
            val endPoint = PointF(lineEndX, lineY)
            framePath.moveTo(startPoint.x, startPoint.y)
            framePath.lineTo(endPoint.x, endPoint.y)
            paint.style = Paint.Style.STROKE
            paint.color = resources.getColor(R.color.theme01_textColorMain)
            paint.strokeWidth = 1.0f.dp
            m_canvas.drawPath(framePath, paint)
        }
    }

    /**
     *  (public) 服务器返回新数据（准备刷新）
     */
    fun refreshCandleLayerPrepare(kdata: JSONArray) {
        _first_init_draw = false
        _kdata = kdata
        //  重置
        _currCandleOffset = 0
        _panOffsetX = 0.0f
        //  处理数据
        prepareAllDatas(_kdata)
        //  刷新（新数据不偏移，显示最新数据。）
        postDraw()
    }

    /**
     * 提交刷新
     */
    private fun postDraw() {
        postInvalidate()
        crossView?.postDraw()
    }

    /**
     * 刷新K线主体
     */
    private fun refreshCandleLayerCore(offset_number: Int) {
        //  1、清理
        clearAllLayer()
        //  2、根据当前缩放计算屏幕可显示数量
        _maxShowNumber = calcMaxShowCandleNumber(_currCandleWidth.toFloat())
        //  3、准备显示用数据（所有蜡烛图坐标等各种数据）
        prepareShowData(_maxShowNumber, offset_number)
        //  4、描绘
        drawCore(_maxShowNumber, _currCandleWidth.toFloat())
    }

    /**
     *  (private) 重绘前清理所有图层
     */
    private fun clearAllLayer() {
        clearAllMaLayer()
    }

    private fun clearAllMaLayer() {
        //  什么也不处理
    }

    constructor(context: Context) : super(context) {
        _context = context
        init()
    }

    constructor(context: Context, sw: Float, baseAsset: JSONObject, quoteAsset: JSONObject) : super(context) {
        _context = context
        _view_size = SizeF(sw, sw)

        //  外部参数
        _baseAsset = baseAsset
        _quoteAsset = quoteAsset
        _base_precision = baseAsset.getInt("precision")
        _quote_precision = quoteAsset.getInt("precision")
        _base_id = baseAsset.getString("id").toString()

        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context) {
        _context = context
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context) {
        _context = context
        init()
    }

    private fun init() {

        _currCandleOffset = 0
        _panOffsetX = 0f

        setPaints()

        _kdata = JSONArray()

        //  初始化各种数据
        ekdptType = EKlineDatePeriodType.ekdpt_15m //  默认值


        val fTotalHeight: Float = _view_size.height
        fOneCellHeight = fTotalHeight / kBTS_KLINE_ROW_NUM
        fMainGraphHeight = fOneCellHeight * (kBTS_KLINE_ROW_NUM - 1)
        fMainMAHeight = fOneCellHeight * kBTS_KLINE_MA_HEIGHT

        val fSecondGraphTotal: Float = fOneCellHeight - fMainMAHeight
        fSecondMAHeight = fSecondGraphTotal * kBTS_KLINE_MA_HEIGHT
        fSecondGraphHeight = fSecondGraphTotal - fSecondMAHeight

        //  初始化model池
        _kdataModelPool = mutableListOf()
        for (i in 0 until kBTS_KLINE_MAX_SHOW_CANDLE_NUM) {
            _kdataModelPool!!.add(MKlineItemData())
        }

        _kdataModelCurrentIndex = 0

        _currMaxPrice = null
        _currMinPrice = null
        _currRowPriceStep = null
        _currMaxVolume = null

        //  初始化默认字体
        _fontname = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        _fontsize = kBTS_KLINE_PRICE_VOL_FONTSIZE

        //  REMARK：测量X轴、Y轴、MAX、MIN价格、VOL等字体高度用。
        var _size: Size = auxSizeWithText("0.123456789", _fontname!!, _fontsize!!)
        _f10NumberSize = SizeF(_size.width.toFloat(), _size.height.toFloat())

        // 设置手势事件代理
        m_event_delegate = GestureDetector(_context, KlineEventDelegate())
        m_scale_event_delegate = ScaleGestureDetector(_context, KlineScaleEventDelegate())

        // 缩放手势
        _currCandleTotalWidth = kBTS_KLINE_CANDLE_WIDTH + kBTS_KLINE_SHADOW_WIDTH
        _currCandleWidth = _currCandleTotalWidth - kBTS_KLINE_SHADOW_WIDTH
    }

    // 设置画笔
    private fun setPaints() {

        // 背景
        m_paint01.strokeWidth = 2.0f.dp
        m_paint01.color = resources.getColor(R.color.theme01_bottomLineColor)

        // 上涨颜色
        m_paint02.strokeWidth = 2.0f.dp
        m_paint02.color = resources.getColor(R.color.theme01_buyColor)

        // 下跌颜色
        m_paint03.strokeWidth = 2.0f.dp
        m_paint03.color = resources.getColor(R.color.theme01_sellColor)
        m_paint03.isAntiAlias = true
    }

    // 主刷新
    override fun onDraw(canvas: Canvas) {
        m_canvas = canvas
        super.onDraw(m_canvas)
        if (_first_init_draw) {
            return
        }
        //  描绘背景
        genBackFrameLayer(RectF(0f, 0f, _view_size.width, _view_size.height))
        //  描绘K线主体
        refreshCandleLayerCore(_currCandleOffset)
    }

    /**
     * 计算坐标并描绘十字叉（提交到十字叉view进行描绘）
     */
    private fun _drawCrossLayer(event: MotionEvent) {
        //  获取坐标
        val point = PointF(event.x, event.y)
        val x = Math.min(max(point.x, 0f), _view_size.width)

        //  计算选中索引
        val fWidthCandle = _currCandleWidth * 2 + kBTS_KLINE_SHADOW_WIDTH
        val fRealWidth = fWidthCandle + kBTS_KLINE_INTERVAL
        cross_candle_index = Math.min(max(round(x / fRealWidth), 0f), _kdataArrayShowing.count() - 1f).toInt()
        //  提交描绘
        crossView!!.postDrawCrossLayer(_kdataArrayShowing[cross_candle_index])
    }

    /**
     * 计算拖拽偏移量并重绘
     */
    private fun _moveViewWithX(x: Float) {
        val offsetX = x.toInt()

        var fWidthCandle = _currCandleWidth * 2 + kBTS_KLINE_SHADOW_WIDTH
        var fRealWidth = fWidthCandle + kBTS_KLINE_INTERVAL

        val offset_candle = (max(_panOffsetX + offsetX, 0f) / fRealWidth).roundToInt()
        if (offset_candle != _currCandleOffset) {
            _currCandleOffset = offset_candle
            //  重绘
            postDraw()
        }
    }

    /**
     * 处理缩放
     */
    private fun _onScaleTrigger(scale: Float) {
        val total_width = min(max((_currCandleTotalWidth * scale).roundToInt(), kBTS_KLINE_CANDLE_WIDTH_MIN + kBTS_KLINE_SHADOW_WIDTH), kBTS_KLINE_CANDLE_WIDTH_MAX + kBTS_KLINE_SHADOW_WIDTH)
        if (total_width != _currCandleTotalWidth) {
            _currCandleTotalWidth = total_width
            _currCandleWidth = _currCandleTotalWidth - kBTS_KLINE_SHADOW_WIDTH
            //  重绘
            postDraw()
        }
    }

    /**
     * 重载屏幕点击事件
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        //  无数据
        if (_kdataArrayShowing.count() <= 0) {
            return false
        }

        //  长按手势中
        if (long_press) {
            val action = event.action
            if (action == MotionEvent.ACTION_MOVE) {
                //  移动重新描绘
                _drawCrossLayer(event)
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                //  取消长按标记
                long_press = false
                //  取消描绘
                crossView!!.postDrawCrossLayer(null)
            }
            return true
        }

        //  拖拽手势中
        if (pan_gesture) {
            val action = event.action
            if (action == MotionEvent.ACTION_MOVE) {
                //  处理移动
                _moveViewWithX(event.x - _startTouch.x)
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                //  取消长按标记
                pan_gesture = false
                //  更新偏移量
                _panOffsetX += event.x - _startTouch.x
                _panOffsetX = max(_panOffsetX, 0.0f)
                val spaceW = _currCandleWidth * 2 + kBTS_KLINE_SHADOW_WIDTH + kBTS_KLINE_INTERVAL
                _panOffsetX = min(_panOffsetX, max(_kdataArrayAll.size * spaceW - _view_size.width, 0.0f))
            }
            return true
        }

        //  缩放手势中
        if (_scale_gesture) {
            val action = event.action
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                _scale_gesture = false
                //  REMARK：取消 down 标记，否则在缩放取消的瞬间会触发【长按】手势，并且之后不会有任何 done、up、move事件，导致十字叉不会消失。
                long_press_down = false
                return true
            } else {
                return m_scale_event_delegate!!.onTouchEvent(event)
            }
        }

        //  转到手势判断
        if (event.pointerCount >= 2) {
            parent.requestDisallowInterceptTouchEvent(true)
            return m_scale_event_delegate!!.onTouchEvent(event)
        } else {
            return m_event_delegate!!.onTouchEvent(event)
        }
    }

    /**
     * 处理缩放手势
     */
    internal inner class KlineScaleEventDelegate : ScaleGestureDetector.OnScaleGestureListener {

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            _scale_gesture = true
            _scaleStartPan = detector.currentSpan
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            //  REMARK：不使用 detector.scaleFactor，这里采用最初的 span 计算缩放率。
            val scale = detector.currentSpan / _scaleStartPan
            _onScaleTrigger(scale)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            //  这里什么都不处理
        }
    }

    /**
     * 长按手势、拖拽手势触发判断
     */
    internal inner class KlineEventDelegate : GestureDetector.SimpleOnGestureListener() {
        /**
         * 点击事件
         */
        override fun onDown(e: MotionEvent?): Boolean {
            //  设置标记
            long_press_down = true
            //  REMARK：必须返回 true，不然后续的长按和拖拽不会触发。
            return true
        }

        /**
         * 拖拽手势触发
         */
        override fun onScroll(firstDownEvent: MotionEvent, lastMoveEvent: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            //  缩放中不处理该手势
            if (_scale_gesture) {
                return false
            }
            assert(_kdataArrayShowing.size > 0)
            parent.requestDisallowInterceptTouchEvent(true)
            //  设置标记（并记录第一次 down 时的坐标）
            pan_gesture = true
            _startTouch = PointF(firstDownEvent.x, firstDownEvent.y)
            //  处理移动
            _moveViewWithX(lastMoveEvent.x - _startTouch.x)
            return true
        }

        /**
         * 长按手势触发
         */
        override fun onLongPress(e: MotionEvent) {
            if (crossView == null) {
                return
            }
            //  缩放中不处理该手势
            if (_scale_gesture) {
                return
            }
            //  没 down 事件，不处理。
            if (!long_press_down) {
                return
            }
            assert(_kdataArrayShowing.size > 0)
            //  重要！！！禁止父View打断move事件
            parent.requestDisallowInterceptTouchEvent(true)
            //  设置长按标记
            long_press = true
            //  描绘十字叉
            _drawCrossLayer(e)
        }
    }

}