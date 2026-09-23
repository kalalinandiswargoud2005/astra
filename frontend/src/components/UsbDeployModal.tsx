import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Usb, HardDrive, RefreshCw, Zap, CheckCircle2, AlertTriangle, X, Laptop, Terminal, Server, ShieldCheck, Wifi, Copy, Check, Download, ExternalLink } from 'lucide-react';
import { Button, Card } from '@/components/ui';
import api from '@/lib/api';
import { toast } from 'sonner';

interface UsbDeployModalProps {
  isOpen: boolean;
  onClose: () => void;
}

interface UsbDrive {
  driveLetter: string;
  displayName: string;
  totalSpaceBytes: number;
  freeSpaceBytes: number;
  removable: boolean;
}

export function UsbDeployModal({ isOpen, onClose }: UsbDeployModalProps) {
  const [deployMode, setDeployMode] = useState<'network' | 'usb'>('network');
  const [copiedCmd, setCopiedCmd] = useState<boolean>(false);
  const [drives, setDrives] = useState<UsbDrive[]>([]);
  const [selectedDrive, setSelectedDrive] = useState<string>('');
  const [targetName, setTargetName] = useState<string>('Target-Laptop-01');
  const [customFolderName, setCustomFolderName] = useState<string>('');
  const [serverUrl, setServerUrl] = useState<string>('');
  const [hostIp, setHostIp] = useState<string>('');
  const [isScanning, setIsScanning] = useState<boolean>(false);
  const [isDeploying, setIsDeploying] = useState<boolean>(false);
  const [progress, setProgress] = useState<number>(0);
  const [deployResult, setDeployResult] = useState<any>(null);

  const previewFolderName = customFolderName.trim()
    ? customFolderName.trim().replace(/[^a-zA-Z0-9-_]/g, '_')
    : `ASTRA_AGENT_${(targetName.trim() || 'Target-Laptop').replace(/[^a-zA-Z0-9-_]/g, '_')}`;

  const fallbackHost = typeof window !== 'undefined' && window.location.hostname && window.location.hostname !== 'localhost'
    ? window.location.hostname 
    : '127.0.0.1';
  const effectiveHost = hostIp || fallbackHost;
  const effectiveServerUrl = serverUrl.trim() || `http://${effectiveHost}:8080`;
  const networkCommand = `New-Item -ItemType Directory -Path "C:\\Astra\\Agent" -Force | Out-Null; Invoke-WebRequest -Uri "${effectiveServerUrl}/api/v1/agent/binary/download" -OutFile "C:\\Astra\\Agent\\windows-agent.jar" -UseBasicParsing; $env:ASTRA_BACKEND_URL="${effectiveServerUrl}"; Start-Process "java" -ArgumentList "-Djava.awt.headless=false -Xmx512m -jar C:\\Astra\\Agent\\windows-agent.jar"`;

  const handleCopyCommand = () => {
    navigator.clipboard.writeText(networkCommand);
    setCopiedCmd(true);
    toast.success('PowerShell Enrollment Command Copied!', {
      description: 'Paste into Administrator PowerShell on target laptop.'
    });
    setTimeout(() => setCopiedCmd(false), 3000);
  };

  const fetchDrives = async () => {
    setIsScanning(true);
    try {
      const res = await api.get('/usb/drives');
      const data = res.data;
      setDrives(data.drives || []);
      setHostIp(data.hostIp || '127.0.0.1');
      if (!serverUrl) {
        setServerUrl(data.defaultServerUrl || `http://${data.hostIp}:8080`);
      }
      
      // Auto-select first removable drive or fallback to first drive
      const removable = (data.drives || []).find((d: UsbDrive) => d.removable);
      if (removable) {
        setSelectedDrive(removable.driveLetter);
      } else if (data.drives && data.drives.length > 0) {
        setSelectedDrive(data.drives[0].driveLetter);
      }
    } catch (err) {
      toast.error('Failed to scan USB drives from backend');
    } finally {
      setIsScanning(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      setDeployResult(null);
      setProgress(0);
      fetchDrives();
    }
  }, [isOpen]);

  const handleDeploy = async () => {
    if (!selectedDrive) {
      toast.error('Please select a target USB drive or laptop drive');
      return;
    }

    setIsDeploying(true);
    setProgress(15);

    try {
      const timer = setInterval(() => {
        setProgress((prev) => (prev < 85 ? prev + 15 : prev));
      }, 300);

      const res = await api.post('/usb/deploy', {
        drivePath: selectedDrive,
        targetHostname: targetName,
        serverUrl: serverUrl,
        customFolderName: customFolderName.trim() || undefined
      });

      clearInterval(timer);
      setProgress(100);
      setDeployResult(res.data);
      toast.success('Agent Package Flashed to USB!', {
        description: `Staged installer in ${res.data.folderName || previewFolderName}`
      });
    } catch (err: any) {
      toast.error('USB Agent Deployment Failed', {
        description: err.response?.data?.message || 'Writing to USB failed.'
      });
    } finally {
      setIsDeploying(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-md p-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95, y: 15 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 15 }}
        className="w-full max-w-2xl bg-[#020617] border border-primary/50 p-6 font-mono shadow-[0_0_40px_rgba(0,240,255,0.25)] relative text-white rounded-none"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-primary/30 pb-4 mb-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary/10 border border-primary/40 cyber-cut">
              {deployMode === 'network' ? (
                <Wifi size={22} className="text-primary animate-pulse" />
              ) : (
                <Usb size={22} className="text-primary animate-pulse" />
              )}
            </div>
            <div>
              <h2 className="font-rajdhani font-bold text-xl tracking-wider text-white flex items-center gap-2">
                DEVICE ENROLLMENT & AGENT DEPLOYMENT
              </h2>
              <p className="text-xs text-white/50">
                Enroll target laptops over local Wi-Fi / network (No USB) or flash an offline USB installer.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white/40 hover:text-white transition-colors p-1 cursor-pointer"
          >
            <X size={20} />
          </button>
        </div>

        {/* Mode Selector Tabs */}
        <div className="flex border-b border-primary/20 mb-5">
          <button
            type="button"
            onClick={() => setDeployMode('network')}
            className={`flex items-center gap-2 px-4 py-2.5 text-xs font-mono font-bold uppercase transition-all cursor-pointer border-b-2 ${
              deployMode === 'network'
                ? 'border-primary text-primary bg-primary/10 shadow-[0_0_15px_rgba(0,240,255,0.15)]'
                : 'border-transparent text-white/50 hover:text-white hover:bg-white/5'
            }`}
          >
            <Wifi size={14} className={deployMode === 'network' ? 'text-primary animate-pulse' : ''} />
            <span>Wi-Fi / Network Enroll (No USB)</span>
            <span className="text-[9px] bg-primary/20 text-primary border border-primary/40 px-1.5 py-0.5 ml-1">FASTEST</span>
          </button>
          <button
            type="button"
            onClick={() => setDeployMode('usb')}
            className={`flex items-center gap-2 px-4 py-2.5 text-xs font-mono font-bold uppercase transition-all cursor-pointer border-b-2 ${
              deployMode === 'usb'
                ? 'border-secondary text-secondary bg-secondary/10 shadow-[0_0_15px_rgba(112,0,255,0.15)]'
                : 'border-transparent text-white/50 hover:text-white hover:bg-white/5'
            }`}
          >
            <Usb size={14} />
            <span>Flash to USB Storage</span>
          </button>
        </div>

        {!deployResult ? (
          deployMode === 'network' ? (
            /* Network / Wi-Fi Direct Enrollment (No USB Needed) */
            <div className="space-y-4">
              <div className="p-3.5 bg-primary/10 border border-primary/40 flex items-start gap-3">
                <Wifi size={20} className="text-primary shrink-0 mt-0.5" />
                <div>
                  <h4 className="font-bold text-primary text-xs uppercase tracking-wider">
                    Zero-USB Direct Wi-Fi Auto-Enrollment
                  </h4>
                  <p className="text-[11px] text-white/70 mt-0.5">
                    Target laptop only needs to be on the same Wi-Fi / LAN network. Run the 1-line command below in Administrator PowerShell on that laptop to download, launch, and register it automatically.
                  </p>
                </div>
              </div>

              {/* Hostname & SOC URL */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-surface p-3 border border-border-color">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-white/70 mb-1 flex items-center gap-1.5">
                    <Laptop size={14} className="text-secondary" /> Target Node Hostname
                  </label>
                  <input
                    type="text"
                    value={targetName}
                    onChange={(e) => setTargetName(e.target.value)}
                    placeholder="e.g. Target-Laptop-01"
                    className="w-full bg-black/40 border border-border-color px-3 py-1.5 text-xs text-white focus:border-primary focus:outline-none font-mono"
                  />
                  <div className="text-[10px] text-white/40 mt-1">
                    Node identifier in dashboard
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-white/70 mb-1 flex items-center gap-1.5">
                    <Server size={14} className="text-primary" /> SOC Backend URL
                  </label>
                  <input
                    type="text"
                    value={serverUrl}
                    onChange={(e) => setServerUrl(e.target.value)}
                    placeholder={`http://${effectiveHost}:8080`}
                    className="w-full bg-black/40 border border-border-color px-3 py-1.5 text-xs text-white focus:border-primary focus:outline-none font-mono"
                  />
                  <div className="text-[10px] text-white/40 mt-1">
                    Detected LAN IP: <span className="text-primary font-bold">{effectiveHost}</span>
                  </div>
                </div>
              </div>

              {/* PowerShell Command Box */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label className="text-xs font-bold uppercase tracking-wider text-primary flex items-center gap-1.5">
                    <Terminal size={14} /> Run on Target Laptop (PowerShell as Administrator):
                  </label>
                  <span className="text-[10px] text-emerald-400 font-mono flex items-center gap-1">
                    <Check size={12} /> Auto-Configured
                  </span>
                </div>
                <div className="p-3 bg-black/80 border border-primary/40 rounded-none relative group">
                  <pre className="text-[11px] font-mono text-emerald-400 whitespace-pre-wrap break-all select-all leading-relaxed">
                    {networkCommand}
                  </pre>
                </div>
              </div>

              {/* Instructions */}
              <div className="bg-surface/60 border border-border-color p-3 space-y-1 text-xs text-white/80">
                <div className="font-bold text-secondary uppercase tracking-wider text-[11px] flex items-center gap-1 mb-1">
                  <ShieldCheck size={13} /> Quick 3-Step Setup:
                </div>
                <div className="pl-1 space-y-1 text-[11px] text-white/70">
                  <div><span className="text-primary font-bold">1.</span> On target laptop, press <kbd className="bg-white/10 px-1 border border-white/20">Win + X</kbd> $\rightarrow$ choose <strong>Terminal / PowerShell (Admin)</strong>.</div>
                  <div><span className="text-primary font-bold">2.</span> Click <span className="text-success font-bold">"Copy PowerShell Command"</span> below, paste into PowerShell, and press <strong>Enter</strong>.</div>
                  <div><span className="text-primary font-bold">3.</span> The agent will download, start silently, and immediately appear in the <strong>Active Nodes</strong> dashboard!</div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center justify-between gap-3 pt-3 border-t border-border-color">
                <Button 
                  variant="outline" 
                  onClick={() => window.open(`${effectiveServerUrl}/api/v1/agent/binary/download`, '_blank')}
                  className="flex items-center gap-2 text-xs font-mono uppercase cursor-pointer"
                >
                  <Download size={13} /> Download JAR Manually
                </Button>
                <div className="flex items-center gap-2">
                  <Button variant="outline" onClick={onClose} className="cursor-pointer">
                    Close
                  </Button>
                  <Button
                    variant="primary"
                    onClick={handleCopyCommand}
                    className="flex items-center gap-2 shadow-[0_0_20px_rgba(0,240,255,0.4)] cursor-pointer text-xs uppercase"
                  >
                    {copiedCmd ? <Check size={14} className="text-emerald-300" /> : <Copy size={14} />}
                    {copiedCmd ? 'Command Copied!' : 'Copy PowerShell Command'}
                  </Button>
                </div>
              </div>
            </div>
          ) : (
            /* USB Staging Mode */
            <div className="space-y-5">
              {/* USB Drive Selector */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-bold uppercase tracking-wider text-primary flex items-center gap-2">
                    <HardDrive size={14} /> Connected USB Storage / Target Drives
                  </label>
                  <button
                    onClick={fetchDrives}
                    disabled={isScanning}
                    className="text-xs text-secondary hover:text-white flex items-center gap-1 transition-colors cursor-pointer"
                  >
                    <RefreshCw size={12} className={isScanning ? 'animate-spin' : ''} />
                    <span>Scan Hardware</span>
                  </button>
                </div>

                {isScanning ? (
                  <div className="p-4 bg-surface border border-border-color text-center text-white/50 text-xs animate-pulse">
                    Scanning system for USB hardware connections...
                  </div>
                ) : drives.length === 0 ? (
                  <div className="p-4 bg-danger/10 border border-danger/40 text-danger text-xs flex items-center gap-2">
                    <AlertTriangle size={16} />
                    <span>No USB drives detected. Connect target laptop USB drive and click Scan Hardware. Or use the <strong>Wi-Fi / Network Enroll</strong> tab above!</span>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    {drives.map((drive) => (
                      <div
                        key={drive.driveLetter}
                        onClick={() => setSelectedDrive(drive.driveLetter)}
                        className={`p-3 border cursor-pointer transition-all flex items-center justify-between ${
                          selectedDrive === drive.driveLetter
                            ? 'border-primary bg-primary/10 shadow-[0_0_15px_rgba(0,240,255,0.2)]'
                            : 'border-border-color bg-surface hover:border-primary/40'
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          <Usb size={18} className={drive.removable ? 'text-success' : 'text-primary'} />
                          <div>
                            <span className="font-bold text-sm text-white">{drive.displayName}</span>
                            <div className="text-[11px] text-white/50">
                              {drive.driveLetter} ({(drive.freeSpaceBytes / (1024 * 1024 * 1024)).toFixed(1)} GB Free)
                            </div>
                          </div>
                        </div>
                        {drive.removable && (
                          <span className="text-[10px] bg-success/20 text-success border border-success/40 px-1.5 py-0.5 font-bold uppercase">
                            USB
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Target Laptop Config & Hostname Editor */}
              <div className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-white/70 mb-1 flex items-center gap-1.5">
                      <Laptop size={14} className="text-secondary" /> Target Device Hostname
                    </label>
                    <input
                      type="text"
                      value={targetName}
                      onChange={(e) => setTargetName(e.target.value)}
                      placeholder="e.g. Target-Laptop-01"
                      className="w-full bg-surface border border-border-color px-3 py-2 text-sm text-white focus:border-primary focus:outline-none font-mono"
                    />
                    {/* Quick Preset Chips */}
                    <div className="flex flex-wrap gap-1.5 mt-1.5">
                      {['Target-Laptop-01', 'Target-Laptop-02', 'Target-PC', 'SOC-Node'].map((preset) => (
                        <button
                          key={preset}
                          type="button"
                          onClick={() => setTargetName(preset)}
                          className={`text-[10px] px-1.5 py-0.5 border cursor-pointer transition-colors ${
                            targetName === preset
                              ? 'border-primary bg-primary/20 text-primary font-bold'
                              : 'border-border-color text-white/50 hover:text-white hover:border-white/40'
                          }`}
                        >
                          {preset}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-bold uppercase tracking-wider text-white/70 mb-1 flex items-center gap-1.5">
                      <Server size={14} className="text-primary" /> C2 Server URL
                    </label>
                    <input
                      type="text"
                      value={serverUrl}
                      onChange={(e) => setServerUrl(e.target.value)}
                      placeholder={`http://${hostIp}:8080`}
                      className="w-full bg-surface border border-border-color px-3 py-2 text-sm text-white focus:border-primary focus:outline-none font-mono"
                    />
                    <div className="text-[10px] text-white/40 mt-1">
                      Auto-detected LAN host IP: <span className="text-primary">{hostIp}</span>
                    </div>
                  </div>
                </div>

                {/* Custom Folder Name & Path Preview */}
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-white/70 mb-1 flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <Terminal size={14} className="text-accent" /> Custom Folder Name on USB (Optional)
                    </span>
                    <span className="text-[11px] text-white/40 lowercase font-normal">
                      leave blank for auto-generated name
                    </span>
                  </label>
                  <input
                    type="text"
                    value={customFolderName}
                    onChange={(e) => setCustomFolderName(e.target.value)}
                    placeholder={previewFolderName}
                    className="w-full bg-surface border border-border-color px-3 py-2 text-sm text-white focus:border-primary focus:outline-none font-mono"
                  />
                  <div className="text-[11px] text-white/50 mt-1">
                    USB Staging Path:{' '}
                    <span className="text-primary font-bold">
                      {selectedDrive || 'E:\\'}{previewFolderName}
                    </span>
                  </div>
                </div>

                {/* Deployment Manifest Summary */}
                <div className="p-3 bg-surface/50 border border-border-color text-xs text-white/70 space-y-1">
                  <div className="text-white/90 font-bold uppercase tracking-wider text-[11px] flex items-center gap-1">
                    <ShieldCheck size={14} className="text-success" /> Auto-Provisioned Package Contents:
                  </div>
                  <div>• <span className="text-success font-mono">windows-agent.jar</span> (Core Engine)</div>
                  <div>• <span className="text-primary font-mono">Deploy-Target-Agent.bat</span> (1-Click Auto-Installer)</div>
                  <div>• <span className="text-secondary font-mono">Start-Target-Agent-Interactive.bat</span></div>
                  <div>• <span className="text-white font-mono">Astra-UI.vbs & AstraEDR.xml</span> (HUD)</div>
                </div>
              </div>

              {/* Progress Bar */}
              {isDeploying && (
                <div className="space-y-1.5 pt-2">
                  <div className="flex justify-between text-xs text-primary font-bold">
                    <span>FLASHING AGENT PAYLOAD & INSTALLERS TO USB...</span>
                    <span>{progress}%</span>
                  </div>
                  <div className="h-2 w-full bg-surface border border-border-color overflow-hidden">
                    <motion.div
                      className="h-full bg-gradient-to-r from-primary via-secondary to-success"
                      animate={{ width: `${progress}%` }}
                      transition={{ duration: 0.3 }}
                    />
                  </div>
                </div>
              )}

              {/* Action Buttons */}
              <div className="flex items-center justify-end gap-3 pt-4 border-t border-border-color">
                <Button variant="outline" onClick={onClose} disabled={isDeploying}>
                  Cancel
                </Button>
                <Button
                  variant="primary"
                  onClick={handleDeploy}
                  disabled={isDeploying || !selectedDrive}
                  className="flex items-center gap-2 shadow-[0_0_20px_rgba(0,240,255,0.4)] cursor-pointer"
                >
                  <Zap size={16} />
                  {isDeploying ? 'Flashing USB...' : 'Deploy Agent to USB Laptop'}
                </Button>
              </div>
            </div>
          )
        ) : (
          /* Success Screen */
          <div className="space-y-5">
            <div className="p-4 bg-success/10 border border-success/50 flex items-start gap-3">
              <CheckCircle2 size={24} className="text-success shrink-0 mt-0.5" />
              <div>
                <h3 className="font-bold text-success text-base uppercase">
                  USB PROVISIONING KIT CREATED SUCCESSFULLY!
                </h3>
                <p className="text-xs text-white/80 mt-1">
                  {deployResult.message || `Files staged to ${selectedDrive}${previewFolderName}`}
                </p>
              </div>
            </div>

            <div className="bg-surface border border-border-color p-4 space-y-3">
              <div className="text-xs font-bold uppercase text-primary flex items-center gap-1.5">
                <Terminal size={14} /> Usage on Target Laptop:
              </div>
              <div className="text-xs text-white/60 mb-1">
                Folder created on USB: <span className="text-success font-bold font-mono">{deployResult.folderName || previewFolderName}</span>
              </div>
              <div className="space-y-2 text-xs text-white/80 pl-1">
                <div>
                  <span className="font-bold text-success uppercase">1. One-Click Permanent Service Install (Production):</span>
                  <div className="pl-4 text-white/70 mt-0.5">
                    Open <span className="text-success font-bold font-mono">{deployResult.folderName || previewFolderName}</span> $\rightarrow$ Right-click <span className="text-success font-bold font-mono">Deploy-Target-Agent.bat</span> $\rightarrow$ <span className="text-warning font-bold">"Run as Administrator"</span>.
                  </div>
                </div>
                <div>
                  <span className="font-bold text-secondary uppercase">2. Quick Interactive Runner (Testing/Debug Mode):</span>
                  <div className="pl-4 text-white/70 mt-0.5">
                    Right-click <span className="text-secondary font-bold font-mono">Start-Target-Agent-Interactive.bat</span> $\rightarrow$ <span className="text-warning font-bold">"Run as Administrator"</span>.
                  </div>
                </div>
                <div>
                  <span className="font-bold text-danger uppercase">3. Clean Uninstallation:</span>
                  <div className="pl-4 text-white/70 mt-0.5">
                    Right-click <span className="text-danger font-bold font-mono">Uninstall-Target-Agent.bat</span> $\rightarrow$ <span className="text-warning font-bold">"Run as Administrator"</span>.
                  </div>
                </div>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <Button variant="primary" onClick={onClose} className="flex items-center gap-2 cursor-pointer">
                <ShieldCheck size={16} /> Done & Return to Devices
              </Button>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}
