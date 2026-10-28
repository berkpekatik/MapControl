// HAB native köprü.
//  Adım 1 (nativeProbe): /dev/hab open testi + libuhab dlopen/sembol varlık testi.
//  Adım 2 (nativeSessionTest): mycar kvSy'den çıkarılan protokol ile qcarcam
//    sunucusuna oturum açma denemesi:
//      /dev/hab aç -> kanal aç (ioctl 0xC0100A04, subId=0)
//      -> 32B tanışma başlığı gönder/al -> tip 17 (init) -> tip 0 (giriş listesi)
//      -> tip 19 (kapat) -> kanal kapat.
//    Stream açma / buffer ayırma / kare alma YOK.
#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <algorithm>
#include <cstdio>
#include <string>
#include <sys/mman.h>
#include <vector>

namespace {

// ---- HAB ioctl arayüzü (libuhab ile aynı numaralar, tip 0x0A) ----
constexpr unsigned long kHabIoctlOpen = 0xC0100A04UL;   // nr 4, 0x10 B
constexpr unsigned long kHabIoctlSend = 0x40180A02UL;   // nr 2, 0x18 B
constexpr unsigned long kHabIoctlRecv = 0xC0180A03UL;   // nr 3, 0x18 B
constexpr unsigned long kHabIoctlClose = 0x40080A05UL;  // nr 5, 0x08 B
constexpr unsigned long kHabIoctlExport = 0xC0180A06UL;   // nr 6, 0x18 B
constexpr unsigned long kHabIoctlUnexport = 0x400C0A08UL; // nr 8, 0x0C B
constexpr unsigned long kIonIoctlAlloc = 0xC0184900UL;    // ION_IOC_ALLOC

struct HabOpenReq {
    uint32_t id;    // çıkış: çekirdeğin verdiği kanal id
    uint32_t sub;   // (subId << 16) | 0xC9
    uint32_t neg;   // -1
    uint32_t zero;  // 0
};
struct HabSendReq {
    const void* buf;
    uint32_t chan;
    uint32_t size;
    uint64_t reserved;
};
struct HabRecvReq {
    void* buf;
    uint32_t chan;
    uint32_t size;  // giriş: kapasite, çıkış: gelen boyut
    uint64_t timeout;  // araçtaki libuhab variant 2'de sabit 1 (poll)
};
struct HabCloseReq {
    uint32_t chan;
    uint32_t zero;
};
static_assert(sizeof(HabOpenReq) == 0x10, "open req");
static_assert(sizeof(HabSendReq) == 0x18, "send req");
static_assert(sizeof(HabRecvReq) == 0x18, "recv req");
static_assert(sizeof(HabCloseReq) == 0x08, "close req");

void AppendHex(std::string* out, const uint8_t* data, size_t len, size_t maxLen) {
    size_t n = len < maxLen ? len : maxLen;
    char tmp[4];
    for (size_t i = 0; i < n; i++) {
        snprintf(tmp, sizeof(tmp), "%02x", data[i]);
        *out += tmp;
        if (i + 1 < n) *out += ' ';
    }
    if (len > n) *out += " ...";
}

uint64_t NowNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

int32_t MakeSessionToken() {
    unsigned __int128 m = (unsigned __int128)NowNs() * (unsigned __int128)0x431bde82d7b634dbULL;
    uint32_t hi = (uint32_t)(((uint64_t)(m >> 64)) >> 0x12);
    uint32_t top = (uint32_t)(((uint32_t)(m >> 96)) >> 0x1f);
    return (int32_t)(hi - top) ^ (int32_t)(getpid() << 16);
}

int HabChannelOpen(int fd, uint32_t subId, uint32_t* outId) {
    HabOpenReq req{};
    req.id = 0;
    req.sub = (subId << 16) | 0xC9u;
    req.neg = 0xFFFFFFFFu;
    req.zero = 0;
    if (ioctl(fd, kHabIoctlOpen, &req) != 0) return -errno;
    *outId = req.id;
    return 0;
}

int HabSend(int fd, uint32_t chan, const void* buf, uint32_t size) {
    HabSendReq req{};
    req.buf = buf;
    req.chan = chan;
    req.size = size;
    req.reserved = 0;
    if (ioctl(fd, kHabIoctlSend, &req) != 0) return -errno;
    return 0;
}

// mycar FUN_0012d914 eşdeğeri: deadline'a kadar EAGAIN/EINTR'de tekrar dene.
int HabRecv(int fd, uint32_t chan, void* buf, uint32_t cap, int timeoutMs, uint32_t* outSize) {
    uint64_t deadline = NowNs() / 1000000ULL + (uint64_t)timeoutMs;
    int polls = 0;
    while (true) {
        if (NowNs() / 1000000ULL >= deadline) return -110;  // -ETIMEDOUT
        HabRecvReq req{};
        req.buf = buf;
        req.chan = chan;
        req.size = cap;
        req.timeout = 1;  // variant 2 davranışı
        if (ioctl(fd, kHabIoctlRecv, &req) == 0) {
            *outSize = req.size;
            return 0;
        }
        int e = errno;
        if (e != EAGAIN && e != EINTR) return -e;
        // Kontrol cevaplari genelde ilk birkac ms'de gelir; uzayan beklemede
        // (kare bekleme) uyanma sayisini azaltmak icin araligi 4 ms'ye kadar acar.
        long sleepNs = polls < 4 ? 1000000L : (polls < 8 ? 2000000L : 4000000L);
        polls++;
        struct timespec ts{0, sleepNs};
        nanosleep(&ts, nullptr);
    }
}

int HabTransact(int fd, uint32_t chan, void* buf, uint32_t size, uint32_t cap,
                int timeoutMs, uint32_t* outSize) {
    int r = HabSend(fd, chan, buf, size);
    if (r != 0) return r;
    return HabRecv(fd, chan, buf, cap, timeoutMs, outSize);
}

int HabChannelClose(int fd, uint32_t chan) {
    HabCloseReq req{};
    req.chan = chan;
    req.zero = 0;
    if (ioctl(fd, kHabIoctlClose, &req) != 0) return -errno;
    return 0;
}

std::string SessionTest() {
    std::string out = "HAB Oturum: ";

    int fd = open("/dev/hab", O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        out += "open(/dev/hab)=FAIL errno=" + std::to_string(errno);
        return out;
    }
    out += "fd OK | ";

    uint32_t chan = 0;
    int r = HabChannelOpen(fd, 0, &chan);
    if (r != 0 || chan == 0) {
        out += "kanal ac=FAIL ret=" + std::to_string(r) + " id=" + std::to_string(chan);
        close(fd);
        return out;
    }
    out += "kanal id=" + std::to_string(chan) + " | ";

    // Tanışma başlığı (32B): {0,3} {token,pid} {0x407,0x407} {flag=1,0}
    uint32_t header[8] = {};
    header[0] = 0;
    header[1] = 3;
    header[2] = (uint32_t)MakeSessionToken();
    header[3] = (uint32_t)getpid();
    header[4] = 0x407;
    header[5] = 0x407;
    header[6] = 1;  // flag baytı (+24)
    header[7] = 0;

    r = HabSend(fd, chan, header, sizeof(header));
    if (r != 0) {
        out += "baslik gonder=FAIL ret=" + std::to_string(r);
        HabChannelClose(fd, chan);
        close(fd);
        return out;
    }
    out += "baslik gonderildi (token=0x";
    {
        char tmp[16];
        snprintf(tmp, sizeof(tmp), "%08x", header[2]);
        out += tmp;
    }
    out += ") | ";

    uint8_t resp[64] = {};
    uint32_t respSize = 0;
    r = HabRecv(fd, chan, resp, sizeof(resp), 750, &respSize);
    if (r != 0) {
        out += "baslik cevap=FAIL ret=" + std::to_string(r);
        HabChannelClose(fd, chan);
        close(fd);
        return out;
    }
    uint32_t respA = 0, respB = 0;
    memcpy(&respA, resp, 4);
    if (respSize >= 8) memcpy(&respB, resp + 4, 4);
    out += "cevap " + std::to_string(respSize) + "B A=" + std::to_string(respA) +
           " B=" + std::to_string(respB) + " [";
    AppendHex(&out, resp, respSize, 32);
    out += "] | ";

    // Kontrol kanalı (A): subId = cevapA + 1 (mycar beginSession akışı)
    uint32_t chanA = 0;
    r = HabChannelOpen(fd, respA + 1, &chanA);
    if (r != 0 || chanA == 0) {
        out += "kanalA ac=FAIL ret=" + std::to_string(r) + " id=" + std::to_string(chanA);
        HabChannelClose(fd, chan);
        close(fd);
        return out;
    }
    out += "kanalA id=" + std::to_string(chanA) + " | ";

    // Tip 17: oturum başlat (kanal A üzerinden)
    uint8_t msg[64] = {};
    uint64_t type17 = 17;
    memcpy(msg, &type17, 8);
    uint32_t msgSize = 0;
    r = HabSend(fd, chanA, msg, 8);
    if (r == 0) {
        r = HabRecv(fd, chanA, msg, sizeof(msg), 750, &msgSize);
        if (r != 0) r = -1000 + r;  // recv hatasını send'den ayır
    } else {
        r = -2000 + r;
    }
    if (r != 0) {
        out += "tip17=FAIL ret=" + std::to_string(r);
        HabChannelClose(fd, chanA);
        HabChannelClose(fd, chan);
        close(fd);
        return out;
    }
    uint32_t respType = 0;
    memcpy(&respType, msg, 4);
    out += "tip17 cevap " + std::to_string(msgSize) + "B tip=" + std::to_string(respType) + " [";
    AppendHex(&out, msg, msgSize, 32);
    out += "] | ";

    // Tip 0: giriş listesi (0x1418B tampon, kayıtlar +0x14'ten, 0x140'ar bayt)
    static uint8_t listBuf[0x1418];
    memset(listBuf, 0, sizeof(listBuf));
    uint32_t hasOut = 1, capacity = 16;
    memcpy(listBuf + 8, &hasOut, 4);
    memcpy(listBuf + 12, &capacity, 4);
    uint32_t listSize = 0;
    r = HabTransact(fd, chanA, listBuf, sizeof(listBuf), sizeof(listBuf), 1000, &listSize);
    if (r != 0) {
        out += "tip0=FAIL ret=" + std::to_string(r);
    } else {
        uint32_t c12 = 0, c16 = 0;
        memcpy(&c12, listBuf + 12, 4);
        memcpy(&c16, listBuf + 16, 4);
        out += "tip0 cevap " + std::to_string(listSize) + "B adayAdet[12]=" + std::to_string(c12) +
               " [16]=" + std::to_string(c16) + " hdr[";
        AppendHex(&out, listBuf, listSize, 32);
        out += "]";
        // Her kayıtta sıfırdan farklı dword'leri dök (descriptor düzenini çözmek için)
        for (int i = 0; i < 16; i++) {
            size_t off = 0x14 + (size_t)i * 0x140;
            if (listSize < off + 0x140) break;
            out += " | K" + std::to_string(i) + ":";
            bool any = false;
            for (int d = 0; d < 0x140 / 4; d++) {
                uint32_t v = 0;
                memcpy(&v, listBuf + off + d * 4, 4);
                if (v != 0) {
                    char tmp[32];
                    snprintf(tmp, sizeof(tmp), " +%x=%x", d * 4, v);
                    out += tmp;
                    any = true;
                }
            }
            if (!any) out += " (bos)";
        }
    }
    out += " | ";

    // Tip 19: oturum kapat
    uint8_t bye[64] = {};
    uint64_t type19 = 19;
    memcpy(bye, &type19, 8);
    uint32_t byeSize = 0;
    r = HabTransact(fd, chanA, bye, 8, sizeof(bye), 300, &byeSize);
    out += "tip19=" + std::string(r == 0 ? "OK" : "FAIL ret=" + std::to_string(r)) + " | ";

    HabChannelClose(fd, chanA);
    HabChannelClose(fd, chan);
    close(fd);
    out += "kanal kapatildi";
    return out;
}

struct HabExportReq {
    void* ptr;
    uint32_t chan;
    uint32_t size;
    uint64_t handle;  // giriş: 0x100000000, çıkış: export handle
};
struct HabUnexportReq {
    uint32_t chan;
    uint32_t handle;
    uint32_t one;
};
static_assert(sizeof(HabExportReq) == 0x18, "export req");
static_assert(sizeof(HabUnexportReq) == 0x0C, "unexport req");

struct IonAllocReq {
    uint64_t len;
    uint32_t heapMask;
    uint32_t flags;
    int32_t fd;
    uint32_t pad;
};
static_assert(sizeof(IonAllocReq) == 24, "ion alloc");

// Oturum açma adımları (SessionTest ile aynı); başarılıysa fd/kanallar dolu döner.
struct Session {
    int fd = -1;
    uint32_t chanHs = 0;
    uint32_t chanA = 0;
    uint32_t respA = 0;
};

bool OpenSession(std::string* out, Session* s) {
    s->fd = open("/dev/hab", O_RDWR | O_CLOEXEC);
    if (s->fd < 0) {
        *out += "open(/dev/hab)=FAIL errno=" + std::to_string(errno);
        return false;
    }
    int r = HabChannelOpen(s->fd, 0, &s->chanHs);
    if (r != 0 || s->chanHs == 0) {
        *out += "kanal ac=FAIL ret=" + std::to_string(r);
        return false;
    }
    uint32_t header[8] = {};
    header[0] = 0;
    header[1] = 3;
    header[2] = (uint32_t)MakeSessionToken();
    header[3] = (uint32_t)getpid();
    header[4] = 0x407;
    header[5] = 0x407;
    header[6] = 1;
    header[7] = 0;
    r = HabSend(s->fd, s->chanHs, header, sizeof(header));
    if (r != 0) {
        *out += "baslik gonder=FAIL ret=" + std::to_string(r);
        return false;
    }
    uint8_t resp[64] = {};
    uint32_t respSize = 0;
    r = HabRecv(s->fd, s->chanHs, resp, sizeof(resp), 750, &respSize);
    if (r != 0 || respSize < 8) {
        *out += "baslik cevap=FAIL ret=" + std::to_string(r);
        return false;
    }
    memcpy(&s->respA, resp, 4);
    *out += "oturum A=" + std::to_string(s->respA) + " | ";
    r = HabChannelOpen(s->fd, s->respA + 1, &s->chanA);
    if (r != 0 || s->chanA == 0) {
        *out += "kanalA ac=FAIL ret=" + std::to_string(r);
        return false;
    }
    uint8_t msg[64] = {};
    uint64_t type17 = 17;
    memcpy(msg, &type17, 8);
    uint32_t msgSize = 0;
    r = HabTransact(s->fd, s->chanA, msg, 8, sizeof(msg), 750, &msgSize);
    if (r != 0) {
        *out += "tip17=FAIL ret=" + std::to_string(r);
        return false;
    }
    *out += "tip17 OK | ";
    return true;
}

#define NLOG(...) __android_log_print(ANDROID_LOG_INFO, "HabNative", __VA_ARGS__)

void CloseSession(Session* s, bool sendBye) {
    if (s->fd < 0) return;
    if (sendBye && s->chanA != 0) {
        uint8_t bye[64] = {};
        uint64_t type19 = 19;
        memcpy(bye, &type19, 8);
        uint32_t byeSize = 0;
        HabTransact(s->fd, s->chanA, bye, 8, sizeof(bye), 300, &byeSize);
    }
    if (s->chanA != 0) HabChannelClose(s->fd, s->chanA);
    if (s->chanHs != 0) HabChannelClose(s->fd, s->chanHs);
    close(s->fd);
    s->fd = -1;
}


// ---- Cok kamerali akis yardimcilari (tek kare testi + canli onizleme ortak) ----
// Varsayilanlar (aracta olculen): giris listesi (tip 0) okunamazsa bunlarla acilir.
constexpr uint32_t kDefaultWidth = 1280;
constexpr uint32_t kDefaultHeight = 800;
constexpr uint32_t kDefaultFormat = 0x07080100;
constexpr float kDefaultFps = 25.0f;
constexpr int kCamBuffers = 4;
constexpr uint32_t kCamIds[4] = {5, 4, 8, 9};

// mycar D0 ile ayni beyaz liste. Hepsi 2 bayt/piksel paketli (Y0 U Y1 V) islenir;
// mycar da donusturucuye formati gecirmez.
constexpr uint32_t kSupportedFormats[7] = {0x07080100, 0x07080102, 0x07080176, 0x07080161,
                                           0x07080164, 0x07080169, 0x0708016D};

bool FormatSupported(uint32_t format) {
    for (uint32_t f : kSupportedFormats) {
        if (f == format) return true;
    }
    return false;
}

// mycar D0 kurali: W/H cift ve 2..4096, stride >= W*2, kare boyutu = W*H*2.
bool GeometryValid(uint32_t w, uint32_t h, uint32_t stride, uint64_t bytes) {
    if (w < 2 || h < 2 || w > 4096 || h > 4096 || (w & 1) != 0 || (h & 1) != 0) return false;
    return stride >= w * 2 && bytes == (uint64_t)w * h * 2;
}

// Tip 0 giris listesi kaydi (+0x14'ten 0x140'ar bayt).
struct InputDesc {
    uint32_t id = 0;
    uint32_t width = 0;   // +0xa4
    uint32_t height = 0;  // +0xa8
    float fps = 0;        // +0xac (float)
    uint32_t format = 0;  // +0x120
};

struct CamStream {
    uint32_t inputId = 0;
    uint64_t token = 0;
    uint32_t width = kDefaultWidth;
    uint32_t height = kDefaultHeight;
    uint32_t stride = kDefaultWidth * 2;
    uint32_t format = kDefaultFormat;
    uint32_t frameSize = kDefaultWidth * kDefaultHeight * 2;
    // ION tahsis / mmap / export boyutu: sayfa (4096) kati olmali. 1920x1380 gibi
    // kare boyutlari sayfa kati degil; hizasiz export EINVAL (errno 22) veriyor.
    uint32_t allocSize = kDefaultWidth * kDefaultHeight * 2;
    float fps = kDefaultFps;
    struct Buf {
        int fd = -1;
        void* ptr = nullptr;
        uint32_t handle = 0;
        bool exported = false;
    };
    std::vector<Buf> bufs;
    bool opened = false;
    bool started = false;
};

bool CamOpenInput(Session& s, CamStream& c, std::string* out, uint32_t* chanBSlot) {
    NLOG("CamOpenInput id=%u: tip1 gonderiliyor", c.inputId);
    {
        uint8_t m[24] = {};
        uint32_t type = 1;
        memcpy(m, &type, 4);
        memcpy(m + 8, &c.inputId, 4);
        uint32_t ms = 0;
        int r = HabTransact(s.fd, s.chanA, m, sizeof(m), sizeof(m), 750, &ms);
        if (r != 0) { *out += "tip1=FAIL ret=" + std::to_string(r); return false; }
        memcpy(&c.token, m + 16, 8);
        if (c.token == 0) { *out += "token sifir!"; return false; }
        c.opened = true;
        NLOG("CamOpenInput id=%u: tip1 OK token=0x%llx", c.inputId, (unsigned long long)c.token);
    }
    // Kanal B: ilk tip1'den sonra, tip5'ten once acilmali (sunucu sirasi boyle).
    if (chanBSlot != nullptr && *chanBSlot == 0) {
        NLOG("CamOpenInput id=%u: kanalB aciliyor", c.inputId);
        int r = HabChannelOpen(s.fd, s.respA + 2, chanBSlot);
        if (r != 0 || *chanBSlot == 0) {
            *out += "kanalB ac=FAIL ret=" + std::to_string(r);
            return false;
        }
        NLOG("CamOpenInput id=%u: kanalB OK", c.inputId);
    }
    return true;
}

bool CamConfig(Session& s, int ionFd, CamStream& c, std::string* out) {
    for (int i = 0; i < kCamBuffers; i++) {
        IonAllocReq req{};
        req.len = c.allocSize;
        req.heapMask = 0x02000000;
        req.flags = 0;
        req.fd = -1;
        if (ioctl(ionFd, kIonIoctlAlloc, &req) != 0 || req.fd < 0) {
            *out += "ION alloc=FAIL errno=" + std::to_string(errno); return false;
        }
        void* ptr = mmap(nullptr, c.allocSize, PROT_READ | PROT_WRITE, MAP_SHARED, req.fd, 0);
        if (ptr == MAP_FAILED) {
            *out += "ION mmap=FAIL errno=" + std::to_string(errno);
            close(req.fd);
            return false;
        }
        CamStream::Buf b;
        b.fd = req.fd;
        b.ptr = ptr;
        c.bufs.push_back(b);
    }
    {
        static uint8_t msg[1000];
        memset(msg, 0, sizeof(msg));
        uint32_t type = 5;
        memcpy(msg, &type, 4);
        memcpy(msg + 8, &c.token, 8);
        memcpy(msg + 0x10, &c.format, 4);
        uint32_t count = (uint32_t)c.bufs.size();
        memcpy(msg + 0x20, &count, 4);
        for (size_t i = 0; i < c.bufs.size(); i++) {
            uint8_t* e = msg + 0x28 + i * 0x50;
            memcpy(e + 0x00, &c.width, 4);
            memcpy(e + 0x04, &c.height, 4);
            memcpy(e + 0x08, &c.stride, 4);
            memcpy(e + 0x0C, &c.frameSize, 4);
            uint32_t dmaFd = (uint32_t)c.bufs[i].fd;
            memcpy(e + 0x10, &dmaFd, 4);
            uint32_t one = 1;
            memcpy(e + 0x48, &one, 4);
        }
        NLOG("CamConfig id=%u: tip5 gonderiliyor", c.inputId);
        int r = HabSend(s.fd, s.chanA, msg, sizeof(msg));
        if (r != 0) { *out += "tip5 gonder=FAIL ret=" + std::to_string(r); return false; }
        NLOG("CamConfig id=%u: tip5 gonderildi, adet bekleniyor", c.inputId);
        uint32_t serverCount = 0;
        uint32_t got = 0;
        r = HabRecv(s.fd, s.chanA, &serverCount, 4, 1000, &got);
        if (r != 0 || got < 4) { *out += "adet cevap=FAIL ret=" + std::to_string(r); return false; }
        for (size_t i = 0; i < c.bufs.size(); i++) {
            HabExportReq req{};
            req.ptr = c.bufs[i].ptr;
            req.chan = s.chanA;
            req.size = c.allocSize;
            req.handle = 0x100000000ULL;
            if (ioctl(s.fd, kHabIoctlExport, &req) != 0) {
                *out += "export=FAIL errno=" + std::to_string(errno); return false;
            }
            c.bufs[i].handle = (uint32_t)(req.handle & 0xFFFFFFFFu);
            c.bufs[i].exported = true;
            uint64_t ack = (uint64_t)c.bufs[i].handle;
            r = HabSend(s.fd, s.chanA, &ack, 8);
            if (r != 0) { *out += "ack=FAIL ret=" + std::to_string(r); return false; }
        }
        NLOG("CamConfig id=%u: export+ack bitti, tip5 cevap bekleniyor", c.inputId);
        uint32_t ms = 0;
        r = HabRecv(s.fd, s.chanA, msg, sizeof(msg), 1000, &ms);
        if (r != 0) { *out += "tip5 cevap=FAIL ret=" + std::to_string(r); return false; }
        NLOG("CamConfig id=%u: tip5 OK", c.inputId);
    }
    return true;
}

bool CamStartStream(Session& s, CamStream& c, std::string* out) {
    uint8_t m[16] = {};
    uint64_t t = 7;
    memcpy(m, &t, 8);
    memcpy(m + 8, &c.token, 8);
    uint32_t ms = 0;
    NLOG("CamStart id=%u: tip7 gonderiliyor", c.inputId);
    int r = HabTransact(s.fd, s.chanA, m, 16, sizeof(m), 500, &ms);
    if (r != 0) { *out += "tip7=FAIL ret=" + std::to_string(r); return false; }
    c.started = true;
    NLOG("CamStart id=%u: tip7 OK (akista)", c.inputId);
    return true;
}

bool CamOpen(Session& s, int ionFd, CamStream& c, std::string* out, uint32_t* chanBSlot,
             bool /*firstOfSession*/) {
    if (!CamOpenInput(s, c, out, chanBSlot)) return false;
    if (!CamConfig(s, ionFd, c, out)) return false;
    return CamStartStream(s, c, out);
}

void CamClose(Session& s, CamStream& c) {
    if (c.started) {
        uint8_t m[16] = {};
        uint64_t t = 8;
        memcpy(m, &t, 8);
        memcpy(m + 8, &c.token, 8);
        uint32_t ms = 0;
        HabTransact(s.fd, s.chanA, m, 16, sizeof(m), 300, &ms);
        c.started = false;
    }
    if (c.opened) {
        uint8_t m[16] = {};
        uint64_t t = 2;
        memcpy(m, &t, 8);
        memcpy(m + 8, &c.token, 8);
        uint32_t ms = 0;
        HabTransact(s.fd, s.chanA, m, 16, sizeof(m), 300, &ms);
        c.opened = false;
    }
    for (auto& b : c.bufs) {
        if (b.exported) {
            HabUnexportReq req{s.chanA, b.handle, 1};
            ioctl(s.fd, kHabIoctlUnexport, &req);
            b.exported = false;
        }
        if (b.ptr != nullptr) { munmap(b.ptr, c.allocSize); b.ptr = nullptr; }
        if (b.fd >= 0) { close(b.fd); b.fd = -1; }
    }
    c.bufs.clear();
}

// outStatus/outRet: teshis icin son tip11 durum kodu ve transact sonucu.
int CamGrab(Session& s, uint32_t chanB, CamStream& c, int attempts, int sleepMs,
            uint32_t* outStatus = nullptr, int* outRet = nullptr) {
    if (outStatus != nullptr) *outStatus = 0xFFFFFFFFu;
    if (outRet != nullptr) *outRet = 0;
    for (int attempt = 0; attempt < attempts; attempt++) {
        uint8_t m[80] = {};
        uint32_t type = 11;
        memcpy(m, &type, 4);
        memcpy(m + 8, &c.token, 8);
        uint64_t timeoutNs = 500000000ULL;
        memcpy(m + 16, &timeoutNs, 8);
        uint32_t ms = 0;
        int r = HabTransact(s.fd, chanB, m, sizeof(m), sizeof(m), 1000, &ms);
        if (outRet != nullptr) *outRet = r;
        if (r != 0) return -2;
        uint32_t status = 0;
        memcpy(&status, m + 4, 4);
        if (outStatus != nullptr) *outStatus = status;
        if (status == 0) {
            uint32_t idx = 0;
            memcpy(&idx, m + 0x20, 4);
            if (idx >= c.bufs.size()) {
                if (outStatus != nullptr) *outStatus = 0xFFFFFFFEu;
                return -2;
            }
            return (int)idx;
        }
        if (sleepMs > 0) {
            struct timespec ts{0, (long)sleepMs * 1000000L};
            nanosleep(&ts, nullptr);
        }
    }
    return -1;
}

void CamRelease(Session& s, CamStream& c, uint32_t idx) {
    uint8_t m[24] = {};
    uint64_t t = 12;
    memcpy(m, &t, 8);
    memcpy(m + 8, &c.token, 8);
    memcpy(m + 16, &idx, 4);
    uint32_t ms = 0;
    HabTransact(s.fd, s.chanA, m, sizeof(m), sizeof(m), 500, &ms);
}

// ---- Kamera hub'i ----
// Sunucu tek oturumda ikinci tip5'i cevapsiz birakiyor; her kameraya kendi
// oturumu (kendi /dev/hab fd + tanisma + kanalA/B) acilir. Kameralar birbirinden
// bagimsiz acilir/kapanir/yeniden baslatilir (kismi acilis + kendini toparlama).
//
// Kilit sirasi: gHubMutex -> Slot::io -> Slot::hold (tersi asla).
//  io  : oturum, kanallar, ION buffer'lari; HAB islemleri bu kilitle siralanir.
//        Kapatma, devam eden kopya bitene kadar bekler (yoksa munmap edilen
//        buffer'a memcpy SIGSEGV yapar - 11:00 cokmesi).
//  hold: sifir kopya modunda GL'in tuttugu buffer indeksleri.
constexpr int kCopyNone = 0;
constexpr int kCopyFull = 1;
constexpr int kCopyHalf = 2;  // her ikinci satir: W x H/2 YUYV (izgara/mozaik icin yeterli)

// Giris listesi (tip 0), kamera oturumlarindan ayri kisa bir oturumla alinir:
// aracta dogrulanan sira 17 -> 0 -> 19; kamera oturumlarinin 17 -> 1 -> 5 -> 7
// akisina dokunulmaz.
bool QueryInputList(std::vector<InputDesc>* out) {
    out->clear();
    Session s;
    std::string step;
    if (!OpenSession(&step, &s)) {
        NLOG("giris listesi: oturum acilamadi: %s", step.c_str());
        CloseSession(&s, false);
        return false;
    }
    static uint8_t listBuf[0x14 + 16 * 0x140];
    memset(listBuf, 0, sizeof(listBuf));
    uint32_t hasOut = 1, capacity = 16;
    memcpy(listBuf + 8, &hasOut, 4);
    memcpy(listBuf + 12, &capacity, 4);
    uint32_t listSize = 0;
    int r = HabTransact(s.fd, s.chanA, listBuf, sizeof(listBuf), sizeof(listBuf), 1000, &listSize);
    CloseSession(&s, true);
    if (r != 0) {
        NLOG("giris listesi: tip0=FAIL ret=%d", r);
        return false;
    }
    for (int k = 0; k < 16; k++) {
        size_t off = 0x14 + (size_t)k * 0x140;
        if (listSize < off + 0x140) break;
        const uint8_t* e = listBuf + off;
        InputDesc d;
        memcpy(&d.id, e + 0x00, 4);
        memcpy(&d.width, e + 0xa4, 4);
        memcpy(&d.height, e + 0xa8, 4);
        memcpy(&d.fps, e + 0xac, 4);
        memcpy(&d.format, e + 0x120, 4);
        if (d.width == 0 && d.height == 0 && d.format == 0) continue;
        NLOG("giris listesi: id=%u %ux%u fps=%.1f format=0x%08x", d.id, d.width, d.height,
             (double)d.fps, d.format);
        out->push_back(d);
    }
    return !out->empty();
}

struct Slot {
    std::mutex io;
    bool active = false;
    Session session;
    uint32_t chanB = 0;
    CamStream cam;
    uint64_t lastFrameNs = 0;
    uint64_t lastGrabFailLogNs = 0;
    uint64_t intervalNs = 40000000ULL;

    std::mutex hold;
    int latestIdx = -1;   // en yeni kare, GL henuz almadi
    int inUseIdx = -1;    // GL'in su an cizdigi kare
    std::vector<int> pendingRelease;  // GL'in birakti; grab thread'i tip12 ile iade eder
    uint32_t generation = 0;          // her acilis/kapanista artar (GL dokularini yeniler)
    int dmaFds[kCamBuffers] = {-1, -1, -1, -1};
    uint32_t zcWidth = kDefaultWidth;   // dmaFds'in geometrisi (hold kilidiyle)
    uint32_t zcHeight = kDefaultHeight;
};
Slot gSlots[4];
// 0=bilinmiyor, 1=sinyal var, 2=yok/desteklenmiyor
std::atomic<int> gSlotStatus[4];
std::mutex gHubMutex;
int gIonFd = -1;
bool gHubStarted = false;

// gHubMutex ile korunur. Liste alinamazsa kameralar varsayilan geometriyle acilir.
std::vector<InputDesc> gInputs;
bool gInputsValid = false;
uint64_t gInputsTriedNs = 0;
constexpr uint64_t kInputsRetryNs = 30000000000ULL;

// Java'ya acik kamera geometrisi (HubGrab'in io kilidini beklememek icin ayri kilit).
std::mutex gDescMutex;
InputDesc gSlotDesc[4];  // id=0: kamera kapali

// Slot bazinda son hata (gDescMutex ile): kod 0=yok, 1=id listede yok,
// 2=desteklenmeyen format, 3=gecersiz geometri, 4=acilis adimi basarisiz.
int gSlotErrCode[4] = {0, 0, 0, 0};
std::string gSlotErrDetail[4];

void SetSlotError(int i, int code, const std::string& detail) {
    std::lock_guard<std::mutex> g(gDescMutex);
    gSlotErrCode[i] = code;
    gSlotErrDetail[i] = detail;
}

void EnsureInputListLocked(bool force) {
    uint64_t now = NowNs();
    if (gInputsValid || (!force && gInputsTriedNs != 0 && now - gInputsTriedNs < kInputsRetryNs)) {
        return;
    }
    gInputsTriedNs = now;
    gInputsValid = QueryInputList(&gInputs);
    if (!gInputsValid) NLOG("giris listesi yok; varsayilan 1280x800 kullaniliyor");
}

// Listedeki descriptor'i kameraya uygular. Uyumsuzluk artik sert engel degil:
// kamera varsayilanlarla (veya kismi descriptor'la) acilmaya calisilir, sebep
// hata kodu olarak saklanir (uzaktan teshis: kullanici etikette gercek nedeni gorur).
void SlotApplyDescriptorLocked(int slot, CamStream& c) {
    if (!gInputsValid) return;
    const InputDesc* d = nullptr;
    for (const InputDesc& e : gInputs) {
        if (e.id == c.inputId) { d = &e; break; }
    }
    if (d == nullptr) {
        NLOG("kamera id=%u giris listesinde yok; varsayilanla deneniyor", c.inputId);
        SetSlotError(slot, 1, "id=" + std::to_string(c.inputId));
        return;
    }
    uint32_t stride = d->width * 2;
    uint64_t bytes = (uint64_t)d->width * d->height * 2;
    if (!GeometryValid(d->width, d->height, stride, bytes)) {
        NLOG("kamera id=%u gecersiz geometri %ux%u; varsayilanla deneniyor", c.inputId,
             d->width, d->height);
        SetSlotError(slot, 3, std::to_string(d->width) + "x" + std::to_string(d->height));
        return;
    }
    c.width = d->width;
    c.height = d->height;
    c.stride = stride;
    c.format = d->format;
    c.frameSize = (uint32_t)bytes;
    c.allocSize = (uint32_t)((bytes + 4095) & ~4095ULL);
    if (d->fps >= 1.0f && d->fps <= 120.0f) c.fps = d->fps;
    if (!FormatSupported(d->format)) {
        char hex[16];
        snprintf(hex, sizeof(hex), "0x%08x", d->format);
        NLOG("kamera id=%u beyaz liste disi format %s; yine de deneniyor", c.inputId, hex);
        SetSlotError(slot, 2, hex);
    }
}

void PublishSlotDesc(int i, const CamStream* c) {
    std::lock_guard<std::mutex> g(gDescMutex);
    gSlotDesc[i] = InputDesc();
    if (c != nullptr) {
        gSlotDesc[i].id = c->inputId;
        gSlotDesc[i].width = c->width;
        gSlotDesc[i].height = c->height;
        gSlotDesc[i].fps = c->fps;
        gSlotDesc[i].format = c->format;
    }
}

void SlotResetHoldLocked(Slot& s) {
    s.latestIdx = -1;
    s.inUseIdx = -1;
    s.pendingRelease.clear();
    for (int& fd : s.dmaFds) {
        if (fd >= 0) close(fd);
        fd = -1;
    }
    s.generation++;
}

bool SlotOpenLocked(int i) {
    Slot& s = gSlots[i];
    s.session = Session();
    s.chanB = 0;
    s.cam = CamStream();
    s.cam.inputId = kCamIds[i];
    PublishSlotDesc(i, nullptr);
    SetSlotError(i, 0, "");
    SlotApplyDescriptorLocked(i, s.cam);
    std::string step;
    bool ok = OpenSession(&step, &s.session);
    if (ok) ok = CamOpenInput(s.session, s.cam, &step, &s.chanB);
    if (ok) ok = CamConfig(s.session, gIonFd, s.cam, &step);
    if (ok) ok = CamStartStream(s.session, s.cam, &step);
    if (!ok) {
        NLOG("kamera id=%u acilamadi: %s", s.cam.inputId, step.c_str());
        {
            std::lock_guard<std::mutex> g(gDescMutex);
            if (gSlotErrCode[i] == 0) gSlotErrCode[i] = 4;
            gSlotErrDetail[i] = step;
        }
        CamClose(s.session, s.cam);
        if (s.chanB != 0) { HabChannelClose(s.session.fd, s.chanB); s.chanB = 0; }
        CloseSession(&s.session, true);
        gSlotStatus[i] = 2;
        return false;
    }
    {
        std::lock_guard<std::mutex> h(s.hold);
        SlotResetHoldLocked(s);
        for (size_t k = 0; k < s.cam.bufs.size() && k < (size_t)kCamBuffers; k++) {
            s.dmaFds[k] = fcntl(s.cam.bufs[k].fd, F_DUPFD_CLOEXEC, 0);
        }
        s.zcWidth = s.cam.width;
        s.zcHeight = s.cam.height;
    }
    s.active = true;
    s.lastFrameNs = 0;
    s.intervalNs = (uint64_t)(1e9 / s.cam.fps);
    gSlotStatus[i] = 1;
    SetSlotError(i, 0, "");
    PublishSlotDesc(i, &s.cam);
    NLOG("kamera id=%u acik %ux%u fps=%.1f format=0x%08x", s.cam.inputId, s.cam.width,
         s.cam.height, (double)s.cam.fps, s.cam.format);
    return true;
}

void SlotCloseLocked(int i) {
    Slot& s = gSlots[i];
    if (!s.active) return;
    {
        std::lock_guard<std::mutex> h(s.hold);
        SlotResetHoldLocked(s);
    }
    PublishSlotDesc(i, nullptr);
    CamClose(s.session, s.cam);
    if (s.chanB != 0) { HabChannelClose(s.session.fd, s.chanB); s.chanB = 0; }
    CloseSession(&s.session, true);
    s.active = false;
}

// Dondurur: acilan kameralarin bit maskesi (bit i = kamera i), -1 = ION yok.
int HubStart() {
    std::lock_guard<std::mutex> lock(gHubMutex);
    if (!gHubStarted) {
        gIonFd = open("/dev/ion", O_RDONLY | O_CLOEXEC);
        if (gIonFd < 0) {
            NLOG("open(/dev/ion)=FAIL errno=%d", errno);
            for (auto& st : gSlotStatus) st = 2;
            return -1;
        }
        gHubStarted = true;
    }
    EnsureInputListLocked(true);
    int mask = 0;
    for (int i = 0; i < 4; i++) {
        std::lock_guard<std::mutex> io(gSlots[i].io);
        if (gSlots[i].active || SlotOpenLocked(i)) mask |= 1 << i;
    }
    NLOG("HubStart: maske=0x%x", mask);
    return mask;
}

bool HubRestart(int i) {
    if (i < 0 || i >= 4) return false;
    std::lock_guard<std::mutex> lock(gHubMutex);
    if (!gHubStarted) return false;
    EnsureInputListLocked(false);
    std::lock_guard<std::mutex> io(gSlots[i].io);
    SlotCloseLocked(i);
    return SlotOpenLocked(i);
}

void HubStop() {
    std::lock_guard<std::mutex> lock(gHubMutex);
    for (int i = 0; i < 4; i++) {
        std::lock_guard<std::mutex> io(gSlots[i].io);
        SlotCloseLocked(i);
        gSlotStatus[i] = 0;
    }
    if (gIonFd >= 0) { close(gIonFd); gIonFd = -1; }
    gHubStarted = false;
    gInputs.clear();
    gInputsValid = false;
    gInputsTriedNs = 0;
    NLOG("HubStop");
}

// 0x07080102 (UYVY: U Y0 V Y1) bayt duzenini YUYV'ye (Y0 U Y1 V) cevirir.
// 1920x1380'lik araclarda goruldu; asagidaki tum tuketiciler YUYV varsayar.
constexpr uint32_t kFormatUyvy = 0x07080102;

void SwapUyvyToYuyv(uint8_t* data, size_t bytes) {
    for (size_t i = 0; i + 3 < bytes; i += 4) {
        uint8_t u = data[i], y0 = data[i + 1], v = data[i + 2], y1 = data[i + 3];
        data[i] = y0;
        data[i + 1] = u;
        data[i + 2] = y1;
        data[i + 3] = v;
    }
}

// 1 = yeni kare, 0 = kare yok, -1 = hata/kapali.
// dst/mode: istege bagli CPU kopyasi. hold: kareyi GL (sifir kopya) icin tut.
int HubGrab(int i, uint8_t* dst, size_t cap, int mode, bool hold) {
    if (i < 0 || i >= 4) return -1;
    Slot& s = gSlots[i];
    std::lock_guard<std::mutex> io(s.io);
    if (!s.active) return -1;
    std::vector<int> toRelease;
    {
        std::lock_guard<std::mutex> h(s.hold);
        toRelease.swap(s.pendingRelease);
        if (!hold && s.latestIdx >= 0) {
            toRelease.push_back(s.latestIdx);
            s.latestIdx = -1;
        }
    }
    for (int idx : toRelease) CamRelease(s.session, s.cam, (uint32_t)idx);

    // Sonraki kare beklenen zamandan once istenmez: yoklama penceresi kisalir.
    if (s.lastFrameNs != 0) {
        uint64_t target = std::min<uint64_t>(s.intervalNs * 6 / 10, 30000000ULL);
        uint64_t elapsed = NowNs() - s.lastFrameNs;
        if (elapsed < target) {
            uint64_t waitNs = target - elapsed;
            struct timespec ts{(time_t)(waitNs / 1000000000ULL), (long)(waitNs % 1000000000ULL)};
            nanosleep(&ts, nullptr);
        }
    }
    uint32_t grabStatus = 0;
    int grabRet = 0;
    int idx = CamGrab(s.session, s.chanB, s.cam, 3, 5, &grabStatus, &grabRet);
    if (idx < 0) {
        uint64_t failNow = NowNs();
        if (failNow - s.lastGrabFailLogNs > 2000000000ULL) {
            s.lastGrabFailLogNs = failNow;
            NLOG("kamera id=%u kare yok: sonuc=%d tip11ret=%d durum=%u", s.cam.inputId,
                 idx, grabRet, grabStatus);
        }
        return idx == -2 ? -1 : 0;
    }
    uint64_t now = NowNs();
    if (s.lastFrameNs != 0) {
        uint64_t d = std::max<uint64_t>(10000000ULL, std::min<uint64_t>(now - s.lastFrameNs, 200000000ULL));
        s.intervalNs = (s.intervalNs * 7 + d) / 8;
    }
    s.lastFrameNs = now;

    const uint8_t* src = (const uint8_t*)s.cam.bufs[idx].ptr;
    const size_t stride = s.cam.stride;
    const uint32_t halfRows = s.cam.height / 2;
    size_t copied = 0;
    if (dst != nullptr && mode == kCopyFull && cap >= s.cam.frameSize) {
        memcpy(dst, src, s.cam.frameSize);
        copied = s.cam.frameSize;
    } else if (dst != nullptr && mode == kCopyHalf && cap >= stride * halfRows) {
        for (uint32_t y = 0; y < halfRows; y++) {
            memcpy(dst + y * stride, src + (size_t)(y * 2) * stride, stride);
        }
        copied = stride * halfRows;
    }
    if (copied > 0 && s.cam.format == kFormatUyvy) {
        SwapUyvyToYuyv(dst, copied);
    }
    if (hold) {
        int drop = -1;
        {
            std::lock_guard<std::mutex> h(s.hold);
            drop = s.latestIdx;
            s.latestIdx = idx;
        }
        if (drop >= 0) CamRelease(s.session, s.cam, (uint32_t)drop);
    } else {
        CamRelease(s.session, s.cam, (uint32_t)idx);
    }
    return 1;
}

// ---- Sifir kopya: ION dma-buf -> EGLImage -> GL_TEXTURE_EXTERNAL_OES ----
// Paketlenmis YUYV, 640x800 ABGR8888 (bayt sirasi R,G,B,A = Y0,U,Y1,V) olarak
// iceri alinir; shader'daki YUYV cozumu kopya yolundaki RGBA doku ile aynidir.
// Tum ZC fonksiyonlari EGL baglami gecerli olan render thread'inde cagrilir.
constexpr EGLint kFourccAbgr8888 = 0x34324241;  // 'A','B','2','4'

struct ZcCam {
    uint32_t generation = 0;
    bool built = false;
    int count = 0;
    EGLImageKHR images[kCamBuffers] = {EGL_NO_IMAGE_KHR, EGL_NO_IMAGE_KHR,
                                       EGL_NO_IMAGE_KHR, EGL_NO_IMAGE_KHR};
    GLuint textures[kCamBuffers] = {0, 0, 0, 0};
};
ZcCam gZc[4];
EGLDisplay gZcDisplay = EGL_NO_DISPLAY;
PFNEGLCREATEIMAGEKHRPROC pEglCreateImageKHR = nullptr;
PFNEGLDESTROYIMAGEKHRPROC pEglDestroyImageKHR = nullptr;
PFNGLEGLIMAGETARGETTEXTURE2DOESPROC pGlEglImageTargetTexture2DOES = nullptr;

bool ZcInit() {
    EGLDisplay dpy = eglGetCurrentDisplay();
    if (dpy == EGL_NO_DISPLAY) return false;
    const char* eglExt = eglQueryString(dpy, EGL_EXTENSIONS);
    const char* glExt = (const char*)glGetString(GL_EXTENSIONS);
    bool ok = eglExt != nullptr && strstr(eglExt, "EGL_EXT_image_dma_buf_import") != nullptr &&
              strstr(eglExt, "EGL_KHR_image_base") != nullptr && glExt != nullptr &&
              strstr(glExt, "GL_OES_EGL_image_external") != nullptr;
    if (ok) {
        pEglCreateImageKHR = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
        pEglDestroyImageKHR = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
        pGlEglImageTargetTexture2DOES =
            (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
        ok = pEglCreateImageKHR != nullptr && pEglDestroyImageKHR != nullptr &&
             pGlEglImageTargetTexture2DOES != nullptr;
    }
    gZcDisplay = ok ? dpy : EGL_NO_DISPLAY;
    NLOG("ZcInit: %s", ok ? "dma-buf import var (sifir kopya)" : "yok (kopya yolu)");
    return ok;
}

void ZcDestroyCam(ZcCam& z) {
    for (int k = 0; k < kCamBuffers; k++) {
        if (z.images[k] != EGL_NO_IMAGE_KHR && pEglDestroyImageKHR != nullptr) {
            pEglDestroyImageKHR(gZcDisplay, z.images[k]);
        }
        z.images[k] = EGL_NO_IMAGE_KHR;
        if (z.textures[k] != 0) glDeleteTextures(1, &z.textures[k]);
        z.textures[k] = 0;
    }
    z.count = 0;
    z.built = false;
}

// YUYV kare, W/2 genislikte ABGR8888 doku olarak baglanir (bir texel = iki piksel).
bool ZcBuildCam(ZcCam& z, const int* fds, uint32_t width, uint32_t height) {
    for (int k = 0; k < kCamBuffers && fds[k] >= 0; k++) {
        EGLint attrs[] = {
            EGL_WIDTH, (EGLint)(width / 2),
            EGL_HEIGHT, (EGLint)height,
            EGL_LINUX_DRM_FOURCC_EXT, kFourccAbgr8888,
            EGL_DMA_BUF_PLANE0_FD_EXT, fds[k],
            EGL_DMA_BUF_PLANE0_OFFSET_EXT, 0,
            EGL_DMA_BUF_PLANE0_PITCH_EXT, (EGLint)(width * 2),
            EGL_NONE};
        EGLImageKHR img = pEglCreateImageKHR(gZcDisplay, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT,
                                             (EGLClientBuffer) nullptr, attrs);
        if (img == EGL_NO_IMAGE_KHR) {
            NLOG("ZC eglCreateImage FAIL egl=0x%x", eglGetError());
            ZcDestroyCam(z);
            return false;
        }
        GLuint tex = 0;
        glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, tex);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        while (glGetError() != GL_NO_ERROR) {
        }
        pGlEglImageTargetTexture2DOES(GL_TEXTURE_EXTERNAL_OES, (GLeglImageOES)img);
        GLenum err = glGetError();
        z.images[k] = img;
        z.textures[k] = tex;
        z.count = k + 1;
        if (err != GL_NO_ERROR) {
            NLOG("ZC glEGLImageTargetTexture2DOES FAIL gl=0x%x", err);
            ZcDestroyCam(z);
            return false;
        }
    }
    return true;
}

// <0: sifir kopya bu cihazda calismiyor (kopya yoluna gec).
// Aksi halde alt 32 bit = doku (0: kare yok), bit 32 = yeni kare.
// Cagiran, onceki cizimi glFinish ile bitirmis olmali (eski kare hemen iade edilir).
int64_t ZcAcquire(int i) {
    if (i < 0 || i >= 4 || gZcDisplay == EGL_NO_DISPLAY) return -1;
    Slot& s = gSlots[i];
    ZcCam& z = gZc[i];
    std::lock_guard<std::mutex> h(s.hold);
    if (!z.built || z.generation != s.generation) {
        ZcDestroyCam(z);
        z.generation = s.generation;
        z.built = true;
        if (s.dmaFds[0] >= 0 && !ZcBuildCam(z, s.dmaFds, s.zcWidth, s.zcHeight)) return -1;
    }
    bool fresh = false;
    if (s.latestIdx >= 0) {
        if (s.inUseIdx >= 0) s.pendingRelease.push_back(s.inUseIdx);
        s.inUseIdx = s.latestIdx;
        s.latestIdx = -1;
        fresh = true;
    }
    if (s.inUseIdx < 0 || s.inUseIdx >= z.count) return 0;
    return ((int64_t)(fresh ? 1 : 0) << 32) | (int64_t)z.textures[s.inUseIdx];
}

// GL artik bu kameradan kare tutmuyor: tutulanlar iade kuyruguna.
void ZcDetach(int i) {
    if (i < 0 || i >= 4) return;
    Slot& s = gSlots[i];
    std::lock_guard<std::mutex> h(s.hold);
    if (s.inUseIdx >= 0) s.pendingRelease.push_back(s.inUseIdx);
    if (s.latestIdx >= 0) s.pendingRelease.push_back(s.latestIdx);
    s.inUseIdx = -1;
    s.latestIdx = -1;
}

void ZcShutdown() {
    for (int i = 0; i < 4; i++) {
        ZcDetach(i);
        std::lock_guard<std::mutex> h(gSlots[i].hold);
        ZcDestroyCam(gZc[i]);
    }
    gZcDisplay = EGL_NO_DISPLAY;
}

// Tek stream testi: input 5 (1280x800@25), 4 buffer, 1 kare al/bırak.
// frameOut != nullptr ise yakalanan kare baytları ona kopyalanır.
std::string StreamTestImpl(std::vector<uint8_t>* frameOut) {
    std::string out = "HAB Stream: ";
    Session s;
    if (!OpenSession(&out, &s)) {
        CloseSession(&s, false);
        return out;
    }
    uint32_t chanB = 0;
    int ionFd = open("/dev/ion", O_RDONLY | O_CLOEXEC);
    if (ionFd < 0) {
        out += "open(/dev/ion)=FAIL errno=" + std::to_string(errno);
        HabChannelClose(s.fd, chanB);
        CloseSession(&s, true);
        return out;
    }
    CamStream cam;
    cam.inputId = 5;
    std::string camOut;
    if (!CamOpen(s, ionFd, cam, &camOut, &chanB, true)) {
        out += "kamera ac=FAIL: " + camOut;
        CamClose(s, cam);
        close(ionFd);
        HabChannelClose(s.fd, chanB);
        CloseSession(&s, true);
        return out;
    }
    out += "kamera id=5 acik | ";
    // Ilk kare (sensor acilisi icin genis deneme penceresi)
    int idx = CamGrab(s, chanB, cam, 30, 100);
    if (idx < 0) {
        out += "tip11 kare gelmedi ret=" + std::to_string(idx);
        CamClose(s, cam);
        close(ionFd);
        HabChannelClose(s.fd, chanB);
        CloseSession(&s, true);
        return out;
    }
    out += "kare buffer=" + std::to_string(idx) + " | ";
    {
        const uint8_t* px = (const uint8_t*)cam.bufs[idx].ptr;
        if (frameOut != nullptr) {
            frameOut->assign(px, px + cam.frameSize);
        }
        uint64_t sum = 0;
        size_t nonzero = 0;
        for (size_t i = 0; i < cam.frameSize; i++) {
            sum += px[i];
            if (px[i] != 0) nonzero++;
        }
        out += "kare toplam=" + std::to_string(sum) + " nonzero=" + std::to_string(nonzero) + "/" +
               std::to_string(cam.frameSize) + " ilk16[";
        AppendHex(&out, px, cam.frameSize, 16);
        out += "] | ";
    }
    CamRelease(s, cam, (uint32_t)idx);
    out += "tip12 OK | ";
    CamClose(s, cam);
    close(ionFd);
    HabChannelClose(s.fd, chanB);
    CloseSession(&s, true);
    out += "temiz kapatildi";
    return out;
}

std::string StreamTest() {
    return StreamTestImpl(nullptr);
}

std::string Probe() {
    std::string out = "HAB Native: ";

    int fd = open("/dev/hab", O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        out += "open(/dev/hab)=FAIL errno=" + std::to_string(errno) + " (" + strerror(errno) + ") | ";
    } else {
        out += "open(/dev/hab)=OK fd=" + std::to_string(fd) + " | ";
        close(fd);
    }

    dlerror();  // clear
    void* handle = dlopen("/vendor/lib64/libuhab.so", RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        const char* err = dlerror();
        out += "dlopen(libuhab)=FAIL ";
        out += (err != nullptr ? err : "unknown");
        return out;
    }
    out += "dlopen(libuhab)=OK | semboller: ";

    static const char* kSymbols[] = {
        "habmm_socket_open", "habmm_socket_close", "habmm_socket_send",
        "habmm_socket_recv", "habmm_export",     "habmm_import",
        "habmm_unexport",    "habmm_unimport",   "hab_DevCtl",
    };
    for (const char* name : kSymbols) {
        dlerror();
        void* sym = dlsym(handle, name);
        out += name;
        out += (sym != nullptr ? "=var " : "=YOK ");
    }
    dlclose(handle);
    return out;
}

}  // namespace

// YUYV kaynagi (srcW x srcH, stride srcW*2) I420 hedefin (dx,dy,w,h) bolgesine olcekler.
// srcH, kameranin tam yuksekligi veya yarisi (her ikinci satir) olabilir.
// Sutun eslemesi bir kez hesaplanir (piksel basina bolme yok).
void ScaleYuyvToI420(const uint8_t* src, int srcW, int srcH, uint8_t* yPlane, uint8_t* uPlane,
                     uint8_t* vPlane, int outW, int dx, int dy, int w, int h) {
    const int SW = srcW;
    const size_t srcStride = (size_t)srcW * 2;
    std::vector<int> lumaX((size_t)w);
    for (int x = 0; x < w; x++) lumaX[x] = (x * SW / w) * 2;
    const int cw = w / 2, ch = h / 2;
    std::vector<int> chromaX((size_t)cw);
    for (int x = 0; x < cw; x++) chromaX[x] = ((x * SW / cw) & ~1) * 2;
    for (int y = 0; y < h; y++) {
        const uint8_t* srow = src + (size_t)(y * srcH / h) * srcStride;
        uint8_t* d = yPlane + (size_t)(dy + y) * outW + dx;
        for (int x = 0; x < w; x++) d[x] = srow[lumaX[x]];
    }
    for (int y = 0; y < ch; y++) {
        const uint8_t* srow = src + (size_t)(y * srcH / ch) * srcStride;
        uint8_t* du = uPlane + (size_t)(dy / 2 + y) * (outW / 2) + dx / 2;
        uint8_t* dv = vPlane + (size_t)(dy / 2 + y) * (outW / 2) + dx / 2;
        for (int x = 0; x < cw; x++) {
            const uint8_t* px = srow + chromaX[x];
            du[x] = px[1];
            dv[x] = px[3];
        }
    }
}

// Kaynagi olmayan (sinyal yok) bolgeyi siyaha boyar.
void FillI420Black(uint8_t* yPlane, uint8_t* uPlane, uint8_t* vPlane, int outW,
                   int dx, int dy, int w, int h) {
    for (int y = 0; y < h; y++) memset(yPlane + (size_t)(dy + y) * outW + dx, 16, (size_t)w);
    for (int y = 0; y < h / 2; y++) {
        memset(uPlane + (size_t)(dy / 2 + y) * (outW / 2) + dx / 2, 128, (size_t)(w / 2));
        memset(vPlane + (size_t)(dy / 2 + y) * (outW / 2) + dx / 2, 128, (size_t)(w / 2));
    }
}

// 4 YUYV kareyi tek I420 mozaige cevirir. Yerlesim onizlemeyle ayni (On,Arka / Sol,Sag).
// Kadran sirasi (cam index -> mozaik pozisyon): GRID {0,3,2,1} tersi.
void ComposeMosaicScaled(const uint8_t* srcs[4], const int srcWidths[4], const int srcHeights[4],
                         uint8_t* out, int outW, int outH) {
    const int QW = outW / 2, QH = outH / 2;
    uint8_t* yPlane = out;
    uint8_t* uPlane = out + (size_t)outW * outH;
    uint8_t* vPlane = uPlane + (size_t)(outW / 2) * (outH / 2);
    static const int kPosOfCam[4] = {0, 3, 2, 1};
    for (int cam = 0; cam < 4; cam++) {
        int pos = kPosOfCam[cam];
        int ox = (pos % 2) * QW;
        int oy = (pos / 2) * QH;
        if (srcs[cam] == nullptr) {
            FillI420Black(yPlane, uPlane, vPlane, outW, ox, oy, QW, QH);
        } else {
            ScaleYuyvToI420(srcs[cam], srcWidths[cam], srcHeights[cam], yPlane, uPlane, vPlane,
                            outW, ox, oy, QW, QH);
        }
    }
}

// Tek kamerayi istenen boyutta I420'ye cevirir (ayri kayit).
void ConvertSingleScaled(const uint8_t* src, int srcW, int srcH, uint8_t* out, int outW,
                         int outH) {
    uint8_t* yPlane = out;
    uint8_t* uPlane = out + (size_t)outW * outH;
    uint8_t* vPlane = uPlane + (size_t)(outW / 2) * (outH / 2);
    if (src == nullptr) {
        FillI420Black(yPlane, uPlane, vPlane, outW, 0, 0, outW, outH);
        return;
    }
    ScaleYuyvToI420(src, srcW, srcH, yPlane, uPlane, vPlane, outW, 0, 0, outW, outH);
}

// --- Kayit overlay'i: 5x7 font ile Y duzlemine tarih/saat + hiz yazar ---
// Sadece gerekli karakterler: 0-9, A H K M S, ve - : / . bosluk
const uint8_t* GlyphFor(char c) {
    static const uint8_t kDigits[10][5] = {
        {0x3E,0x51,0x49,0x45,0x3E},{0x00,0x42,0x7F,0x40,0x00},{0x42,0x61,0x51,0x49,0x46},
        {0x21,0x41,0x45,0x4B,0x31},{0x18,0x14,0x12,0x7F,0x10},{0x27,0x45,0x45,0x45,0x39},
        {0x3C,0x4A,0x49,0x49,0x30},{0x01,0x71,0x09,0x05,0x03},{0x36,0x49,0x49,0x49,0x36},
        {0x06,0x49,0x49,0x29,0x1E}};
    static const uint8_t kA[5] = {0x7E,0x11,0x11,0x11,0x7E};
    static const uint8_t kH[5] = {0x7F,0x08,0x08,0x08,0x7F};
    static const uint8_t kK[5] = {0x7F,0x08,0x14,0x22,0x41};
    static const uint8_t kM[5] = {0x7F,0x02,0x0C,0x02,0x7F};
    static const uint8_t kS[5] = {0x46,0x49,0x49,0x49,0x31};
    static const uint8_t kN[5] = {0x7F,0x04,0x08,0x10,0x7F};
    static const uint8_t kO[5] = {0x3E,0x41,0x41,0x41,0x3E};
    static const uint8_t kR[5] = {0x7F,0x09,0x19,0x29,0x46};
    static const uint8_t kG[5] = {0x3E,0x41,0x49,0x49,0x7A};
    static const uint8_t kL[5] = {0x7F,0x40,0x40,0x40,0x40};
    static const uint8_t kE[5] = {0x7F,0x49,0x49,0x49,0x41};
    static const uint8_t kI[5] = {0x00,0x41,0x7F,0x41,0x00};
    static const uint8_t kT[5] = {0x01,0x01,0x7F,0x01,0x01};
    static const uint8_t kV[5] = {0x1F,0x20,0x40,0x20,0x1F};
    static const uint8_t kColon[5] = {0x00,0x36,0x36,0x00,0x00};
    static const uint8_t kDash[5] = {0x08,0x08,0x08,0x08,0x08};
    static const uint8_t kSlash[5] = {0x20,0x10,0x08,0x04,0x02};
    static const uint8_t kDot[5] = {0x00,0x60,0x60,0x00,0x00};
    static const uint8_t kSpace[5] = {0,0,0,0,0};
    if (c >= '0' && c <= '9') return kDigits[c - '0'];
    switch (c) {
        case 'A': return kA;
        case 'H': return kH;
        case 'K': return kK;
        case 'M': return kM;
        case 'S': return kS;
        case 'N': return kN;
        case 'O': return kO;
        case 'R': return kR;
        case 'G': return kG;
        case 'L': return kL;
        case 'E': return kE;
        case 'I': return kI;
        case 'T': return kT;
        case 'V': return kV;
        case ':': return kColon;
        case '-': return kDash;
        case '/': return kSlash;
        case '.': return kDot;
        default: return kSpace;
    }
}

// I420 karede sol alt koseye yari saydam koyu bant + beyaz metin (2 satir).
void DrawOverlay(uint8_t* yPlane, int width, int height, const char* line1, const char* line2) {
    const int kScale = width >= 1000 ? 3 : 2;
    const int kCharW = 5 * kScale + kScale;  // 18
    const int kCharH = 7 * kScale;           // 21
    const int kPad = 10;
    const int kLineGap = 8;
    auto textW = [&](const char* t) { return (int)strlen(t) * kCharW; };
    int boxW = std::max(textW(line1), textW(line2)) + kPad * 2;
    int boxH = kCharH * 2 + kLineGap + kPad * 2;
    int boxX = 14;
    int boxY = height - boxH - 14;
    if (boxX + boxW > width) boxW = width - boxX;
    // Bant: parlakligi 1/3'e dusur (goruntu tamamen kapanmaz)
    for (int y = boxY; y < boxY + boxH && y < height; y++) {
        uint8_t* row = yPlane + (size_t)y * width;
        for (int x = boxX; x < boxX + boxW && x < width; x++) {
            row[x] = (uint8_t)(row[x] / 3);
        }
    }
    auto drawLine = [&](const char* t, int topY) {
        int cx = boxX + kPad;
        for (const char* p = t; *p; p++, cx += kCharW) {
            const uint8_t* g = GlyphFor(*p);
            for (int col = 0; col < 5; col++) {
                for (int rowBit = 0; rowBit < 7; rowBit++) {
                    if (g[col] & (1 << rowBit)) {
                        for (int dy = 0; dy < kScale; dy++) {
                            for (int dx = 0; dx < kScale; dx++) {
                                int px = cx + col * kScale + dx;
                                int py = topY + rowBit * kScale + dy;
                                if (px < width && py < height) {
                                    yPlane[(size_t)py * width + px] = 235;
                                }
                            }
                        }
                    }
                }
            }
        }
    };
    if (line1 != nullptr && line1[0]) drawLine(line1, boxY + kPad);
    if (line2 != nullptr && line2[0]) drawLine(line2, boxY + kPad + kCharH + kLineGap);
}

// Tek satir metin cizer (arka plan koyulastirmali). scale: piksel katı.
void DrawText(uint8_t* yPlane, int width, int height, const char* text,
              int leftX, int topY, int scale) {
    int charW = 5 * scale + scale;
    int charH = 7 * scale;
    int tw = (int)strlen(text) * charW;
    // Arka plan bandi
    for (int y = topY - 4; y < topY + charH + 4 && y < height; y++) {
        if (y < 0) continue;
        uint8_t* row = yPlane + (size_t)y * width;
        for (int x = leftX - 6; x < leftX + tw + 6 && x < width; x++) {
            if (x < 0) continue;
            row[x] = (uint8_t)(row[x] / 3);
        }
    }
    int cx = leftX;
    for (const char* p = text; *p; p++, cx += charW) {
        const uint8_t* g = GlyphFor(*p);
        for (int col = 0; col < 5; col++) {
            for (int rowBit = 0; rowBit < 7; rowBit++) {
                if (g[col] & (1 << rowBit)) {
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            int px = cx + col * scale + dx;
                            int py = topY + rowBit * scale + dy;
                            if (px >= 0 && px < width && py >= 0 && py < height) {
                                yPlane[(size_t)py * width + px] = 235;
                            }
                        }
                    }
                }
            }
        }
    }
}

// Mozaik kadranlarina konum etiketleri: ust satir ON/ARKA, alt satir SOL/SAG.
void DrawQuadrantLabels(uint8_t* yPlane, int width, int height) {
    static const char* kLabels[4] = {"ON", "ARKA", "SOL", "SAG"};
    int qw = width / 2, qh = height / 2;
    for (int pos = 0; pos < 4; pos++) {
        int ox = (pos % 2) * qw;
        int oy = (pos / 2) * qh;
        DrawText(yPlane, width, height, kLabels[pos], ox + 14, oy + 12, width >= 900 ? 2 : 1);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeProbe(JNIEnv* env, jclass /*clazz*/) {
    std::string result = Probe();
    return env->NewStringUTF(result.c_str());
}

namespace {

bool ValidOutSize(jint w, jint h) {
    return w >= 16 && h >= 16 && w <= 4096 && h <= 4096 && (w % 4) == 0 && (h % 4) == 0;
}

// Kaynak YUYV buffer'i: geometri gecerli ve kapasite W*2*H'ye yetiyorsa adres, degilse null.
const uint8_t* SourceOrNull(JNIEnv* env, jobject buf, int width, int height) {
    if (buf == nullptr || width < 2 || width > 4096 || (width & 1) != 0 || height < 1 ||
        height > 4096) {
        return nullptr;
    }
    void* addr = env->GetDirectBufferAddress(buf);
    jlong cap = env->GetDirectBufferCapacity(buf);
    if (addr == nullptr || cap < (jlong)width * 2 * height) return nullptr;
    return (const uint8_t*)addr;
}

// I420 cikti buffer'i: kapasite w*h*3/2'den kucukse null (tasma yerine cizim atlanir).
uint8_t* I420OutOrNull(JNIEnv* env, jobject buf, jint w, jint h) {
    if (buf == nullptr) return nullptr;
    void* addr = env->GetDirectBufferAddress(buf);
    jlong cap = env->GetDirectBufferCapacity(buf);
    if (addr == nullptr || cap < (jlong)w * h * 3 / 2) return nullptr;
    return (uint8_t*)addr;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeComposeMosaicSized(JNIEnv* env, jclass /*clazz*/,
                                                       jobjectArray srcsArr, jintArray widthsArr,
                                                       jintArray heightsArr, jobject outBuf,
                                                       jint outW, jint outH, jstring line1,
                                                       jstring line2) {
    if (srcsArr == nullptr || widthsArr == nullptr || heightsArr == nullptr ||
        !ValidOutSize(outW, outH)) {
        return;
    }
    if (env->GetArrayLength(srcsArr) < 4 || env->GetArrayLength(widthsArr) < 4 ||
        env->GetArrayLength(heightsArr) < 4) {
        return;
    }
    jint widths[4] = {};
    jint heights[4] = {};
    env->GetIntArrayRegion(widthsArr, 0, 4, widths);
    env->GetIntArrayRegion(heightsArr, 0, 4, heights);
    const uint8_t* srcs[4] = {};
    int srcWidths[4] = {};
    int srcHeights[4] = {};
    for (int i = 0; i < 4; i++) {
        jobject b = env->GetObjectArrayElement(srcsArr, i);
        srcs[i] = SourceOrNull(env, b, widths[i], heights[i]);
        srcWidths[i] = widths[i];
        srcHeights[i] = heights[i];
        if (b != nullptr) env->DeleteLocalRef(b);
    }
    uint8_t* out = I420OutOrNull(env, outBuf, outW, outH);
    if (out == nullptr) return;
    ComposeMosaicScaled(srcs, srcWidths, srcHeights, out, outW, outH);
    DrawQuadrantLabels(out, outW, outH);
    const char* l1 = line1 != nullptr ? env->GetStringUTFChars(line1, nullptr) : nullptr;
    const char* l2 = line2 != nullptr ? env->GetStringUTFChars(line2, nullptr) : nullptr;
    if (l1 != nullptr || l2 != nullptr) {
        DrawOverlay(out, outW, outH, l1 != nullptr ? l1 : "", l2 != nullptr ? l2 : "");
    }
    if (l1 != nullptr) env->ReleaseStringUTFChars(line1, l1);
    if (l2 != nullptr) env->ReleaseStringUTFChars(line2, l2);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeConvertSingle(JNIEnv* env, jclass /*clazz*/,
                                                       jobject srcBuf, jint srcWidth,
                                                       jint srcHeight, jobject outBuf, jint outW,
                                                       jint outH, jstring label, jstring line1,
                                                       jstring line2) {
    if (!ValidOutSize(outW, outH)) return;
    const uint8_t* src = SourceOrNull(env, srcBuf, srcWidth, srcHeight);
    uint8_t* out = I420OutOrNull(env, outBuf, outW, outH);
    if (out == nullptr) return;
    ConvertSingleScaled(src, srcWidth, srcHeight, out, outW, outH);
    const char* lab = label != nullptr ? env->GetStringUTFChars(label, nullptr) : nullptr;
    if (lab != nullptr && lab[0]) {
        DrawText(out, outW, outH, lab, 14, 12, outW >= 900 ? 2 : 1);
    }
    if (lab != nullptr) env->ReleaseStringUTFChars(label, lab);
    const char* l1 = line1 != nullptr ? env->GetStringUTFChars(line1, nullptr) : nullptr;
    const char* l2 = line2 != nullptr ? env->GetStringUTFChars(line2, nullptr) : nullptr;
    if (l1 != nullptr || l2 != nullptr) {
        DrawOverlay(out, outW, outH, l1 != nullptr ? l1 : "", l2 != nullptr ? l2 : "");
    }
    if (l1 != nullptr) env->ReleaseStringUTFChars(line1, l1);
    if (l2 != nullptr) env->ReleaseStringUTFChars(line2, l2);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeSessionTest(JNIEnv* env, jclass /*clazz*/) {
    std::string result = SessionTest();
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeStreamTest(JNIEnv* env, jclass /*clazz*/) {
    std::string result = StreamTest();
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeHubStart(JNIEnv* /*env*/, jclass /*clazz*/) {
    return HubStart();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeHubStop(JNIEnv* /*env*/, jclass /*clazz*/) {
    HubStop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeHubRestart(JNIEnv* /*env*/, jclass /*clazz*/, jint cam) {
    return HubRestart((int)cam) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeHubStatus(JNIEnv* /*env*/, jclass /*clazz*/, jint cam) {
    if (cam < 0 || cam >= 4) return 0;
    return gSlotStatus[cam].load();
}

// Acik kameranin geometrisi: {id, W, H, format, fps*100}; kapaliysa null.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeHubDescriptor(JNIEnv* env, jclass /*clazz*/, jint cam) {
    if (cam < 0 || cam >= 4) return nullptr;
    InputDesc d;
    {
        std::lock_guard<std::mutex> g(gDescMutex);
        d = gSlotDesc[cam];
    }
    if (d.id == 0 || d.width == 0) return nullptr;
    jint v[5] = {(jint)d.id, (jint)d.width, (jint)d.height, (jint)d.format,
                 (jint)(d.fps * 100.0f + 0.5f)};
    jintArray arr = env->NewIntArray(5);
    if (arr != nullptr) env->SetIntArrayRegion(arr, 0, 5, v);
    return arr;
}

// Son hata: "kod|ayrinti" (kodlar SetSlotError yaninda).
extern "C" JNIEXPORT jstring JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeHubError(JNIEnv* env, jclass /*clazz*/, jint cam) {
    if (cam < 0 || cam >= 4) return env->NewStringUTF("0|");
    std::lock_guard<std::mutex> g(gDescMutex);
    std::string out = std::to_string(gSlotErrCode[cam]) + "|" + gSlotErrDetail[cam];
    return env->NewStringUTF(out.c_str());
}

// out null olabilir (sadece hold). 1=yeni kare, 0=kare yok, -1=hata/kapali
extern "C" JNIEXPORT jint JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeHubGrab(JNIEnv* env, jclass /*clazz*/, jint cam,
                                                jobject out, jint mode, jboolean hold) {
    uint8_t* dst = nullptr;
    size_t cap = 0;
    if (out != nullptr && mode != kCopyNone) {
        dst = (uint8_t*)env->GetDirectBufferAddress(out);
        jlong c = env->GetDirectBufferCapacity(out);
        if (dst == nullptr || c <= 0) return -1;
        cap = (size_t)c;
    }
    return HubGrab((int)cam, dst, cap, (int)mode, hold == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeZcInit(JNIEnv* /*env*/, jclass /*clazz*/) {
    return ZcInit() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeZcAcquire(JNIEnv* /*env*/, jclass /*clazz*/, jint cam) {
    return (jlong)ZcAcquire((int)cam);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeZcDetach(JNIEnv* /*env*/, jclass /*clazz*/, jint cam) {
    ZcDetach((int)cam);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeZcShutdown(JNIEnv* /*env*/, jclass /*clazz*/) {
    ZcShutdown();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_mapcontrol_dvr_HabNative_nativeCaptureFrame(JNIEnv* env, jclass /*clazz*/) {
    std::vector<uint8_t> frame;
    std::string result = StreamTestImpl(&frame);
    __android_log_print(ANDROID_LOG_INFO, "HabNative", "%s", result.c_str());
    if (frame.empty()) {
        return nullptr;
    }
    jbyteArray arr = env->NewByteArray((jsize)frame.size());
    if (arr != nullptr) {
        env->SetByteArrayRegion(arr, 0, (jsize)frame.size(), (const jbyte*)frame.data());
    }
    return arr;
}
