#pragma once

#include "mcu.h"

#include "em_ldma.h"

#define MCU_DMA_IRQ_PRIORITY 4

// Maximum length of one DMA transfer.
#define MCU_DMA_MAX_XFER_COUNT \
  ((int)((_LDMA_CH_CTRL_XFERCNT_MASK >> _LDMA_CH_CTRL_XFERCNT_SHIFT) + 1))

typedef enum {
  MCU_DMA_SIZE_1_BYTE = ldmaCtrlSizeByte,   ///< Byte
  MCU_DMA_SIZE_2_BYTES = ldmaCtrlSizeHalf,  ///< Halfword
  MCU_DMA_SIZE_4_BYTES = ldmaCtrlSizeWord   ///< Word
} mcu_dma_data_size_t;

typedef enum {
  MCU_DMA_SIGNAL_NONE = LDMAXBAR_CH_REQSEL_SOURCESEL_NONE,
  MCU_DMA_SIGNAL_TIMER0_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER0_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER0_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER0_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER0UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER0,
  MCU_DMA_SIGNAL_TIMER1_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_TIMER1_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_TIMER1_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_TIMER1_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER1UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER1,
  MCU_DMA_SIGNAL_USART0_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0RXDATAV | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_RXDATAVRIGHT =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0RXDATAVRIGHT | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_TXBL =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0TXBL | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_TXBLRIGHT =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0TXBLRIGHT | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_USART0_TXEMPTY =
    LDMAXBAR_CH_REQSEL_SIGSEL_USART0TXEMPTY | LDMAXBAR_CH_REQSEL_SOURCESEL_USART0,
  MCU_DMA_SIGNAL_I2C0_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_I2C0RXDATAV | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C0,
  MCU_DMA_SIGNAL_I2C0_TXBL = LDMAXBAR_CH_REQSEL_SIGSEL_I2C0TXBL | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C0,
  MCU_DMA_SIGNAL_I2C1_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_I2C1RXDATAV | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C1,
  MCU_DMA_SIGNAL_I2C1_TXBL = LDMAXBAR_CH_REQSEL_SIGSEL_I2C1TXBL | LDMAXBAR_CH_REQSEL_SOURCESEL_I2C1,
  MCU_DMA_SIGNAL_AGC_RSSI = LDMAXBAR_CH_REQSEL_SIGSEL_AGCRSSI | LDMAXBAR_CH_REQSEL_SOURCESEL_AGC,
  MCU_DMA_SIGNAL_PROTIMER_BOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERBOF | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC3 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC3 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_CC4 =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERCC4 | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_POF =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERPOF | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_PROTIMER_WOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_PROTIMERWOF | LDMAXBAR_CH_REQSEL_SOURCESEL_PROTIMER,
  MCU_DMA_SIGNAL_MODEM_DEBUG =
    LDMAXBAR_CH_REQSEL_SIGSEL_MODEMDEBUG | LDMAXBAR_CH_REQSEL_SOURCESEL_MODEM,
  MCU_DMA_SIGNAL_IADC0_IADC_SCAN =
    LDMAXBAR_CH_REQSEL_SIGSEL_IADC0IADC_SCAN | LDMAXBAR_CH_REQSEL_SOURCESEL_IADC0,
  MCU_DMA_SIGNAL_IADC0_IADC_SINGLE =
    LDMAXBAR_CH_REQSEL_SIGSEL_IADC0IADC_SINGLE | LDMAXBAR_CH_REQSEL_SOURCESEL_IADC0,
  MCU_DMA_SIGNAL_TIMER2_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER2_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER2_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER2_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER2UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER2,
  MCU_DMA_SIGNAL_TIMER3_CC0 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3CC0 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_TIMER3_CC1 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3CC1 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_TIMER3_CC2 =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3CC2 | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_TIMER3_UFOF =
    LDMAXBAR_CH_REQSEL_SIGSEL_TIMER3UFOF | LDMAXBAR_CH_REQSEL_SOURCESEL_TIMER3,
  MCU_DMA_SIGNAL_EUSART0_TXBL =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART0TXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART0,
  MCU_DMA_SIGNAL_EUSART0_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART0RXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART0,
  MCU_DMA_SIGNAL_EUSART1_TXBL =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART1TXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART1,
  MCU_DMA_SIGNAL_EUSART1_RXDATAV =
    LDMAXBAR_CH_REQSEL_SIGSEL_EUSART1RXFL | LDMAXBAR_CH_REQSEL_SOURCESEL_EUSART1,
} mcu_dma_signal_t;

typedef bool (*mcu_dma_callback_t)(uint32_t channel, uint32_t sequence_num, void* user_param);

mcu_err_t mcu_dma_init(const int8_t nvic_priority);
mcu_err_t mcu_dma_allocate_channel(uint32_t* channel, mcu_dma_callback_t callback);
mcu_err_t mcu_dma_peripheral_memory(uint32_t channel, mcu_dma_signal_t signal, void* dst, void* src,
                                    bool dst_inc, int len, mcu_dma_data_size_t size,
                                    mcu_dma_callback_t callback, void* user_param);
mcu_err_t mcu_dma_memory_peripheral(int32_t channel, mcu_dma_signal_t signal, void* dst, void* src,
                                    bool src_inc, int len, mcu_dma_data_size_t size,
                                    mcu_dma_callback_t callback, void* user_param);
