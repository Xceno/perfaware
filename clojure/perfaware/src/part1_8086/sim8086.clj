(ns part1-8086.sim8086
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import (java.io ByteArrayOutputStream)))

(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn print-bits
  "Print the bits of any number or a byte with leading zeros.
  See: https://stackoverflow.com/a/43791564"
  [b]
  (let [class-name (.getName (class b))
        is-byte (= "java.lang.Byte" class-name)
        num-bits (clojure.lang.Reflector/getStaticField class-name "SIZE")
        ;format-string (str "~" num-bits "'0b")
        ;_ (println format-string)
        bin-str-fn #(clojure.lang.Reflector/invokeStaticMethod
                      (if is-byte "java.lang.Integer" class-name)
                      "toBinaryString"
                      (to-array [%]))
        bit-string (if is-byte
                     (str/join (take-last 8 (bin-str-fn (Byte/toUnsignedInt b))))
                     (bin-str-fn b))]
    (println (str (str/join (repeat (- num-bits (count bit-string)) \0))
                  bit-string))))

(defn print-byte [n] (print-bits (byte n)))

;(defn print-byte-buffer [^ByteBuffer buffer]
;  (for [x (range (.capacity buffer))]
;    (let [b (.get buffer x)]
;      (println b)
;      (print-bits b)
;      b)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Homework Part1-01
;;; Decode 8086 ASM `mov` instructions
;;;
;;; NOTES:
;;; ---
;;; The first six bits of a multibyte instruction generally contain an opcode that identifies the basic
;;; instruction type: ADD, XOR, etc.
;;;
;;; The following bit, called the D field, specifies the "direction" of the operation:
;;; 1 = the REG field in the second byte identifies the destination operand.
;;; 0 = the REG field identifies the source operand.
;;;
;;; The W field distinguishes between byte and word operations:
;;; 0 = byte, 1 = word.
;;;
;;; Example first byte:  OPCODE,D,W  | 100010 0 1
;;; TODO: Single bit field encoding
;;;
;;; The second byte of the instruction usually identifies the instruction's operands.
;;; MOD - indicates whether one of the operands is in memory or both operands are in registers.
;;; REG - identifies a register that is one of the instruction operands. In a number of instructions
;;;       the immediate-to-memory variety, REG is used as an extension of the opcode to identify
;;;       the type of operation
;;; R/M - (Register/Memory) depends on how the MODe field is set.
;;;       If MOD = 11 (regsiter-to-register mode), then R/M identifies the second register operand.
;;;       If MOD selects memory mode, then R/M indicates how the effective address of the memory
;;;       is to be calculated.
;;;
;;; Example second byte: MOD,REG,R/M | 11 011 001
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce last-input (atom nil))
(defonce last-decode (atom nil))
(defonce last-result (atom nil))


(def OPCODE
  "See Table 4-12. 8086 Instruction Encoding:
  https://edge.edx.org/c4x/BITSPilani/EEE231/asset/8086_family_Users_Manual_1_.pdf"
  {2r100010 :MOV
   2r110001 :MOV
   2r1011   :MOV
   2r101000 :MOV
   2r100011 :MOV})

(def MOD
  "The MOD (mode) field indicates whether one of the operands is in memory
   or whether both operands are in registers."
  {2r00 :memory
   2r01 :memory-8bit
   2r10 :memory-16bit
   2r11 :register})

(def REG
  "The REG (register) field identifies a register that is one of the instruction operands.
   In a number of instructions, chiefly the immediate-to-memory variety,
   REG is used as an extension of the opcode to identify the type of operation"
  {2r000 [:AL :AX]
   2r001 [:CL :CX]
   2r010 [:DL :DX]
   2r011 [:BL :BX]
   2r100 [:AH :SP]
   2r101 [:CH :BP]
   2r110 [:DH :SI]
   2r111 [:BH :DI]})

(def R-M
  "The encoding of the R/M (Register/Memory) field depends on how the MOD field is set.
  If MOD = 11 (regsiter-to-register mode), then R/M identifies the second register operand.
  If MOD selects memory mode, then R/M indicates how the effective address of the memory
  is to be calculated.")

(defn byte->opcode [byte]
  (OPCODE (bit-shift-right byte 2)))

(defn byte->d-bit [byte]
  (bit-and byte 2r00000010))

(defn byte->w-bit [byte]
  (bit-and byte 2r00000001))

(defn byte->mod [byte]
  (MOD (bit-shift-right byte 6)))

(defn byte->reg [byte]
  (bit-shift-right (bit-and byte 2r00111000) 3))

(defn byte->rm [byte]
  (bit-and byte 2r00000111))

(defn decode [[byte-1 byte-2]]
  (let [byte-1 (bit-and byte-1 2r11111111)
        ;_ (print-bits byte-1)
        opcode (byte->opcode byte-1)
        d-bit (byte->d-bit byte-1)
        w-bit (byte->w-bit byte-1)

        byte-2 (bit-and byte-2 2r11111111)
        mod (byte->mod byte-2)
        reg (byte->reg byte-2)
        reg-target ((REG reg) w-bit)
        rm (byte->rm byte-2)
        rm-target ((REG rm) w-bit)

        [source destination] (if (= d-bit 1)
                               [rm-target reg-target]
                               [reg-target rm-target])]
    {:byte1       byte-1
     :byte2       byte-2
     :opcode      opcode
     :d-bit       d-bit
     :w-bit       w-bit
     :mod         mod
     :reg         reg
     :reg-target  reg-target
     :r/m         rm
     :r/m-target  rm-target
     :source      source
     :destination destination}))

(defn re-assemble [{:keys [opcode destination source]}]
  (str (name opcode) " " (name destination) ", " (name source)))

(defn file->bytes [file]
  (with-open [xin (io/input-stream file)
              xout (ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defn file->instructions [file]
  (->> file
       (file->bytes)
       (partition 2)
       (reset! last-input)
       (map decode)
       (reset! last-decode)
       (map re-assemble)
       (reset! last-result)))

(defn instructions->asm-file [instructions original-filename out-file]
  (let [output (->> (concat
                      [(str "; Original-File: " original-filename)
                       (str)
                       (str "bits 16")
                       (str)]
                      instructions)
                    (str/join (System/getProperty "line.separator")))]
    (with-open [f (io/writer out-file)]
      (.write f output))))

(defn binary-file->asm-file [file]
  (let [out-file (str (io/as-url file) "-REBUILT.asm")
        result (file->instructions file)]
    (instructions->asm-file result file out-file)
    (println "Wrote re-assembled file to" out-file)
    result))



(comment
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;; EXAMPLE 1:
  ;;;
  ;;; mov cx, bx
  ;;;--
  ;;; BYTES: 89 D9
  ;;; Bits: 0b10001001 0b11011001
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (file->instructions (io/as-url (io/file "../../resources/part1/listing_0037_single_register_mov")))
  (->> (io/as-url (io/file "../../resources/part1/listing_0037_single_register_mov"))
       (file->bytes)
       (partition 2)
       (reset! last-input)
       (map decode)
       (reset! last-decode)
       (map re-assemble)
       (reset! last-result))


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;; EXAMPLE 2:
  ;;;
  ;;; mov cx, bx
  ;;; mov ch, ah
  ;;; mov dx, bx
  ;;; mov si, bx
  ;;; mov bx, di
  ;;; mov al, cl
  ;;; mov ch, ch
  ;;; mov bx, ax
  ;;; mov bx, si
  ;;; mov sp, di
  ;;; mov bp, ax
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (file->instructions (io/as-url (io/file "../../resources/part1/listing_0038_many_register_mov")))
  (binary-file->asm-file (io/file "../../resources/part1/listing_0038_many_register_mov"))
  (binary-file->asm-file (io/file "../../resources/part1/listing_0038_many_register_mov-REBUILT"))



  (partition 2 @last-input)
  (doall (map println @last-input))

  (str/replace "bla.asm" #".asm" "-REBUILT.asm")

  (Integer/toBinaryString (first @last-input))

  (-> (bit-and 2r00001010 2r00001001) (print-byte))
  (-> (bit-and-not 2r00001010 2r00001001) (print-byte))
  (-> (bit-not 2r00001010) (print-byte))
  (-> (bit-or 2r00001010 2r00001001) (print-byte))
  (-> (bit-xor 2r00001010 2r00001001) (print-byte))


  2r11
  2r0011
  2r1101
  2r10001001
  (print-bits 2r11)
  (print-bits (byte 2r0011))
  (print-bits (byte 2r0011))
  (print-bits 3)
  (print-bits 137)
  (print-bits 2r1)

  (Integer/toString 2r00100010 16)

  (unsigned-bit-shift-right 35289 8)




  #_())


