using System.ComponentModel;
using System.Diagnostics;

namespace Tel2Wheel.Server;

/// <summary>
/// Makes sure Windows Firewall lets the phone's packets in. Checking needs no rights; adding the
/// rule runs netsh elevated, so the user sees one UAC prompt on first start and never again.
/// </summary>
public static class Firewall
{
    private const string RuleName = "Tel2Wheel UDP";

    public static void EnsureRule()
    {
        try
        {
            if (Run("netsh", $"advfirewall firewall show rule name=\"{RuleName}\"", elevated: false) == 0) return;

            Console.WriteLine("Güvenlik duvarı izni ekleniyor; Windows yönetici onayı isteyecek...");
            int code = Run("netsh",
                $"advfirewall firewall add rule name=\"{RuleName}\" dir=in action=allow protocol=UDP " +
                $"localport={Protocol.DefaultPort},{Protocol.BeaconPort} profile=any",
                elevated: true);
            Console.WriteLine(code == 0
                ? "Güvenlik duvarı izni eklendi."
                : "Güvenlik duvarı izni eklenemedi; telefon bağlanamazsa programı yönetici olarak çalıştırın.");
        }
        catch (Win32Exception)
        {
            // UAC prompt declined.
            Console.WriteLine("Güvenlik duvarı izni verilmedi; telefon bağlanamazsa programı yönetici olarak çalıştırın.");
        }
    }

    private static int Run(string file, string args, bool elevated)
    {
        var psi = new ProcessStartInfo(file, args)
        {
            CreateNoWindow = true,
            WindowStyle = ProcessWindowStyle.Hidden,
        };
        if (elevated)
        {
            psi.UseShellExecute = true;
            psi.Verb = "runas";
        }
        else
        {
            psi.UseShellExecute = false;
            psi.RedirectStandardOutput = true;
        }
        using var p = Process.Start(psi)!;
        if (!elevated) p.StandardOutput.ReadToEnd();
        return p.WaitForExit(30_000) ? p.ExitCode : -1;
    }
}
