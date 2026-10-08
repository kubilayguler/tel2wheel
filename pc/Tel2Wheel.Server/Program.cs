using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using Nefarius.ViGEm.Client.Exceptions;
using Tel2Wheel.Server;

Console.OutputEncoding = System.Text.Encoding.UTF8;

int port = Protocol.DefaultPort;
bool selfTest = false;
for (int i = 0; i < args.Length; i++)
{
    switch (args[i])
    {
        case "--port" when i + 1 < args.Length && int.TryParse(args[i + 1], out var p):
            port = p;
            i++;
            break;
        case "--selftest":
            selfTest = true;
            break;
        case "-h" or "--help":
            Console.WriteLine("Tel2Wheel.Server [--port N] [--selftest]");
            return 0;
        default:
            Console.Error.WriteLine($"Bilinmeyen argüman: {args[i]}");
            return 2;
    }
}

try { Process.GetCurrentProcess().PriorityClass = ProcessPriorityClass.High; } catch { }

VirtualWheel wheel;
try
{
    wheel = new VirtualWheel();
}
catch (VigemBusNotFoundException)
{
    const string url = "https://github.com/nefarius/ViGEmBus/releases/latest";
    Console.Error.WriteLine("ViGEmBus sürücüsü bulunamadı. Açılan sayfadan ViGEmBus kurulum dosyasını (.exe) indirip kurun,");
    Console.Error.WriteLine("ardından bu programı yeniden başlatın.");
    try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { Console.Error.WriteLine(url); }
    return Fail();
}

if (!selfTest) Firewall.EnsureRule();

using var _wheel = wheel;
UdpInputServer server;
try
{
    server = new UdpInputServer(port, wheel);
}
catch (SocketException)
{
    Console.Error.WriteLine($"UDP {port} portu kullanımda. Tel2Wheel zaten açık olabilir; diğer pencereyi kapatıp tekrar deneyin.");
    return Fail();
}
using var _server = server;
using var beacon = new BeaconBroadcaster(port);

Console.WriteLine($"Tel2Wheel sunucusu çalışıyor. UDP {port}, keşif {Protocol.BeaconPort}.");
Console.WriteLine("Sanal Xbox 360 denetleyicisi bağlandı (direksiyon = Sol çubuk X, gaz = RT, fren = LT).");
Console.WriteLine("Bu PC'nin adresleri:");
foreach (var a in BeaconBroadcaster.LocalAddresses()) Console.WriteLine($"  {a}");
Console.WriteLine("Çıkmak için Ctrl+C.\n");

var quit = new ManualResetEventSlim();
Console.CancelKeyPress += (_, e) => { e.Cancel = true; quit.Set(); };

if (selfTest)
{
    new Thread(() => RunSelfTest(port, quit)) { IsBackground = true, Name = "selftest" }.Start();
    Console.WriteLine("Self-test: direksiyon sinüs dalgası gönderiliyor (joy.cpl ile izleyin).\n");
}

long lastPackets = 0;
while (!quit.Wait(1000))
{
    long packets = Interlocked.Read(ref server.Packets);
    long rate = packets - lastPackets;
    lastPackets = packets;
    string state = server.Connected ? "BAĞLI " : "bekliyor";
    string line = $"\r[{state}] {server.ClientName,-21} {rate,4} paket/sn  kayıp {server.Lost,-6} " +
                  $"direksiyon {server.LastSteer * 100 / 32767,4}%  gaz {server.LastThrottle * 100 / 255,3}%  fren {server.LastBrake * 100 / 255,3}%   ";
    Console.Write(line);
}
Console.WriteLine();
return 0;

static void RunSelfTest(int port, ManualResetEventSlim quit)
{
    using var s = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
    s.Connect(new IPEndPoint(IPAddress.Loopback, port));
    var buf = new byte[Protocol.InputLength];
    var sw = Stopwatch.StartNew();
    ushort seq = 0;
    while (!quit.IsSet)
    {
        double t = sw.Elapsed.TotalSeconds;
        short steer = (short)(Math.Sin(t * Math.PI / 2) * 32767);
        byte throttle = (byte)((Math.Sin(t * Math.PI) * 0.5 + 0.5) * 255);
        byte brake = (byte)((Math.Cos(t * Math.PI) * 0.5 + 0.5) * 255);
        Protocol.WriteInput(buf, ++seq, steer, throttle, brake);
        s.Send(buf);
        Thread.Sleep(4);
    }
}

// Keeps the window open after an error when started by double-click.
static int Fail()
{
    if (!Console.IsInputRedirected)
    {
        Console.Error.WriteLine();
        Console.Error.WriteLine("Kapatmak için bir tuşa basın.");
        Console.ReadKey(true);
    }
    return 1;
}
