//
//  MKlineItemData.h
//  oplayer
//
//  Created by SYALON on 13-11-20.
//
//

#import <Foundation/Foundation.h>

@interface MKlineItemData : NSObject

@property (nonatomic, assign) NSInteger showIndex;

@property (nonatomic, assign) BOOL isRise;
@property (nonatomic, assign) BOOL isMaxPrice;
@property (nonatomic, assign) BOOL isMinPrice;
@property (nonatomic, assign) BOOL isMax24Vol;

@property (nonatomic, strong) NSDecimalNumber* nPriceOpen;
@property (nonatomic, strong) NSDecimalNumber* nPriceClose;
@property (nonatomic, strong) NSDecimalNumber* nPriceHigh;
@property (nonatomic, strong) NSDecimalNumber* nPriceLow;
@property (nonatomic, strong) NSDecimalNumber* n24Vol;

@property (nonatomic, strong) NSDecimalNumber* ma5;             //  当前MA5、10、30数据，如果数据周期不足5个或者10个或者30个，那对应数据为nil。
@property (nonatomic, strong) NSDecimalNumber* ma10;
@property (nonatomic, strong) NSDecimalNumber* ma30;
@property (nonatomic, strong) NSDecimalNumber* ma60;            //  分时图需要显示

@property (nonatomic, strong) NSDecimalNumber* change;          //  涨跌额
@property (nonatomic, strong) NSDecimalNumber* change_percent;  //  涨跌幅

@property (nonatomic, strong) NSDecimalNumber* vol_ma5;
@property (nonatomic, strong) NSDecimalNumber* vol_ma10;

@property (nonatomic, assign) CGFloat   fOffsetOpen;
@property (nonatomic, assign) CGFloat   fOffsetClose;
@property (nonatomic, assign) CGFloat   fOffsetHigh;
@property (nonatomic, assign) CGFloat   fOffsetLow;
@property (nonatomic, assign) CGFloat   fOffset24Vol;

@property (nonatomic, assign) CGFloat   fOffsetMA5;
@property (nonatomic, assign) CGFloat   fOffsetMA10;
@property (nonatomic, assign) CGFloat   fOffsetMA30;
@property (nonatomic, assign) CGFloat   fOffsetMA60;            //  分时图需要显示

@property (nonatomic, assign) CGFloat   fOffsetVolMA5;
@property (nonatomic, assign) CGFloat   fOffsetVolMA10;

@property (nonatomic, assign) NSTimeInterval date;

- (void)reset;

/**
 *  (public) 解析服务器的K线数据，生成对应的Model。
 *
 *  fillto          - 可为nil。
 *  ceilHandler     - 可为nil。
 */
+ (MKlineItemData*)parseData:(id)data
                      fillto:(MKlineItemData*)fillto
                     base_id:(NSString*)base_id
              base_precision:(NSInteger)base_precision
             quote_precision:(NSInteger)quote_precision
                 ceilHandler:(NSDecimalNumberHandler*)ceilHandler
              percentHandler:(NSDecimalNumberHandler*)percentHandler;

@end
