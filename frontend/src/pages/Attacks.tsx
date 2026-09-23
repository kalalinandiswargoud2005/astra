/**
 * Attacks.tsx
 *
 * Curated Threat Simulation & Real Endpoint Defensive Remediation Control Center.
 * Exclusively features:
 * - 10 Flagship Visual WOW Threats (Target Laptop Screen effects with instant Visual Recoveries)
 * - 20 Curated Real-World Safe Enterprise Threats (Non-destructive, visual, with 1-click recoveries)
 * - Real Physical Endpoint Controls (Firewall, Defender, RDP, Process Snipe)
 * - Real-time command status pipeline: QUEUED -> DELIVERED -> EXECUTING -> VERIFIED SUCCESS
 */

import React, { useState, useEffect, useRef } from 'react';
import { 
  Activity, Zap, ShieldAlert, CheckCircle, Trash2, 
  ShieldCheck, Terminal, AlertTriangle, RefreshCw,
  Eye, Lock, Unlock, Skull, Monitor, Cpu, Radio, Search
} from 'lucide-react';
import { Card, Button, PageContainer, PageHeader, PageSection, Badge } from '@/components/ui';
import { useQuery } from '@tanstack/react-query';
import { useWebSocket } from '@/providers/WebSocketProvider';
import api from '@/lib/api';
import { toast } from 'sonner';
import { useScopedDevice } from '@/contexts/ScopedDeviceContext';

// ── Severity Styling ─────────────────────────────────────────────────────────
function severityClass(severity: string) {
  switch ((severity || '').toUpperCase()) {
    case 'CRITICAL': return 'text-red-400 border-red-500/40 bg-red-500/10';
    case 'HIGH':     return 'text-orange-400 border-orange-500/40 bg-orange-500/10';
    case 'MEDIUM':   return 'text-yellow-400 border-yellow-500/40 bg-yellow-500/10';
    default:         return 'text-green-400 border-green-500/40 bg-green-500/10';
  }
}

interface CommandEntry {
  commandId: string;
  deviceId: string;
  commandType: string;
  target?: string;
  status: string;
  timestamp: string;
  result?: string;
}

interface ActionEntry {
  id: string;
  time: string;
  threatName: string;
  threatType: string;
  severity: string;
  action: string;
}

// ── 10 Flagship Visual WOW Threats Definition ────────────────────────────────
interface VisualThreat {
  id: string;
  title: string;
  icon: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM';
  description: string;
  laptopEffect: string;
  attackType: string;
  recoveryCommand: string;
  recoveryTarget: string;
  recoveryLabel: string;
}

const VISUAL_THREATS_10: VisualThreat[] = [
  {
    id: 'VIS-01',
    title: 'Hacker Skull Wallpaper Hijack',
    icon: '💀',
    severity: 'HIGH',
    description: 'Adversary hijacks the desktop configuration and applies extortion branding.',
    laptopEffect: 'Desktop wallpaper instantly changes to high-definition glowing Red Skull Ransomware warning.',
    attackType: 'HACKER_WALLPAPER',
    recoveryCommand: 'SHOW_CLEAN_VICTORY',
    recoveryTarget: 'Desktop Wallpaper Restored',
    recoveryLabel: '✨ Restore Clean Wallpaper',
  },
  {
    id: 'VIS-02',
    title: 'DarkSide Rogue Stager Window',
    icon: '🪟',
    severity: 'CRITICAL',
    description: 'Spawns active extortion pop-up window running persistence injection routine.',
    laptopEffect: 'Red alert extortion countdown window appears and pins itself on the target laptop screen.',
    attackType: 'DARKSIDE_PAYLOAD',
    recoveryCommand: 'SNIPE_ROGUE_WINDOW',
    recoveryTarget: 'ASTRA SAFE THREAT SIMULATION',
    recoveryLabel: '🎯 Snipe & Force-Close Window',
  },
  {
    id: 'VIS-03',
    title: 'Matrix Cyber Rain Terminal HUD',
    icon: '🌧️',
    severity: 'MEDIUM',
    description: 'Full-screen animated telemetry stream simulating active cyber breach.',
    laptopEffect: 'High-speed green matrix falling code stream covers the entire target laptop screen.',
    attackType: 'MATRIX_RAIN',
    recoveryCommand: 'CLEAR_MATRIX',
    recoveryTarget: 'Matrix Stream Dispersed',
    recoveryLabel: '✨ Disperse Matrix Stream',
  },
  {
    id: 'VIS-04',
    title: 'Ghost Typer Keystroke Injection',
    icon: '⌨️',
    severity: 'HIGH',
    description: 'Simulates BadUSB or rogue process injecting automated keystrokes into active session.',
    laptopEffect: 'Terminal console opens and visibly types commands automatically in real time.',
    attackType: 'GHOST_TYPER',
    recoveryCommand: 'SNIPE_ROGUE_WINDOW',
    recoveryTarget: 'Ghost Typer Shell',
    recoveryLabel: '🛑 Terminate Injection Shell',
  },
  {
    id: 'VIS-05',
    title: 'Canary Ransomware Encryption',
    icon: '🔒',
    severity: 'CRITICAL',
    description: 'Executes rapid reversible encryption on safe canary documents in C:\\Astra\\Demo.',
    laptopEffect: 'Explorer opens showing documents actively changing from .docx/.pdf to .encrypted.',
    attackType: 'SIMULATED_RANSOMWARE',
    recoveryCommand: 'RESTORE_DEMO_FILE',
    recoveryTarget: 'Financials.docx',
    recoveryLabel: '🔓 Decrypt & Revert Files',
  },
  {
    id: 'VIS-06',
    title: 'Stealth RAT Backdoor (Port 44444)',
    icon: '⚡',
    severity: 'CRITICAL',
    description: 'Simulates an interactive remote access trojan socket listener on loopback port 44444.',
    laptopEffect: 'Backdoor telemetry pop-up appears on laptop showing active unauthorized socket binding.',
    attackType: 'STEALTH_RAT_BACKDOOR',
    recoveryCommand: 'STOP_DEMO_LISTENER',
    recoveryTarget: '44444',
    recoveryLabel: '🔌 Sever Socket & Close Port',
  },
  {
    id: 'VIS-07',
    title: 'Remote Workstation Lockdown',
    icon: '🔐',
    severity: 'HIGH',
    description: 'Autonomous EDR response triggers defensive workstation session lockdown.',
    laptopEffect: 'Target Windows laptop immediately locks screen (user must re-authenticate or unlock).',
    attackType: 'LOCK_WORKSTATION',
    recoveryCommand: 'FINAL_VERIFICATION',
    recoveryTarget: 'Session Verified Clean',
    recoveryLabel: '✅ Authorize & Audit Session',
  },
  {
    id: 'VIS-08',
    title: 'Zero-Day Memory Glitch HUD',
    icon: '👾',
    severity: 'CRITICAL',
    description: 'Simulates memory stack buffer overflow and unauthorized heap spray.',
    laptopEffect: 'Laptop screen flashes with an animated cyber memory-corruption glitch overlay.',
    attackType: 'CYBER_GLITCH',
    recoveryCommand: 'SHOW_CLEAN_VICTORY',
    recoveryTarget: 'Memory Buffers Flushed',
    recoveryLabel: '🧹 Flush Memory Buffers',
  },
  {
    id: 'VIS-09',
    title: 'C2 Sonar Radar Beacon Intercept',
    icon: '📡',
    severity: 'HIGH',
    description: 'Continuous sonar radar tracking simulated unauthorized beacon transmission.',
    laptopEffect: 'Rotating green circular sonar radar appears tracking intercepted beacon pulses.',
    attackType: 'RADAR_BEACON',
    recoveryCommand: 'CLEAR_MATRIX',
    recoveryTarget: 'Sonar Radar Neutralized',
    recoveryLabel: '🛑 Stop Radar & Beacon',
  },
  {
    id: 'VIS-10',
    title: 'Task Manager Policy Hijack',
    icon: '⚙️',
    severity: 'HIGH',
    description: 'Simulates malware modifying registry policy to disable Windows Task Manager (DisableTaskMgr).',
    laptopEffect: 'Task Manager is disabled; attempting to open it displays access restriction alert.',
    attackType: 'REGISTRY_HIJACK',
    recoveryCommand: 'RESTORE_DEMO_REGISTRY',
    recoveryTarget: 'DisableTaskMgr',
    recoveryLabel: '🛡️ Restore Task Manager Policy',
  }
];

export function Attacks() {
  const { subscribe } = useWebSocket();
  const { scopedDeviceId } = useScopedDevice();

  const [activeTab, setActiveTab] = useState<'VISUAL_10' | 'REAL_20' | 'CONTROLS'>('VISUAL_10');
  const [selectedDevice, setSelectedDevice] = useState<string>('');
  const [commandQueue, setCommandQueue] = useState<CommandEntry[]>([]);
  const [actionLog, setActionLog] = useState<ActionEntry[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [severityFilter, setSeverityFilter] = useState('ALL');
  const logEndRef = useRef<HTMLDivElement>(null);

  // ── 1. Fetch Registered Devices ──
  const { data: devices = [], refetch: refetchDevices } = useQuery({
    queryKey: ['devices'],
    queryFn: async () => {
      const res = await api.get('/devices');
      return res.data as any[];
    },
    refetchInterval: 5000,
  });

  // Resolve target device
  const targetDevice = scopedDeviceId
    ? devices.find((d: any) => d.id === scopedDeviceId)
    : selectedDevice
    ? devices.find((d: any) => d.id === selectedDevice)
    : devices.find((d: any) => String(d.status).toUpperCase() === 'ONLINE') || devices[0];

  const targetDeviceId = targetDevice?.id;
  const isTargetOnline = targetDevice && String(targetDevice.status).toUpperCase() === 'ONLINE';

  // ── 2. Fetch Curated 20 Threat Scenarios ──
  const { data: scenarios = [], isLoading: isLoadingScenarios } = useQuery({
    queryKey: ['scenarios'],
    queryFn: async () => {
      const res = await api.get('/simulation/scenarios');
      return res.data as any[];
    },
  });

  // ── 3. WebSocket Subscriptions for Live Command Execution ──
  useEffect(() => {
    const unsubThreats = subscribe('threats', (msg: any) => {
      const threat = msg.threat || msg;
      const entry: ActionEntry = {
        id: threat.id || String(Date.now()),
        time: new Date().toLocaleTimeString('en-US', { hour12: false }),
        threatName: threat.name || threat.threatName || 'Security Incident',
        threatType: threat.type || threat.category || 'MALWARE',
        severity: threat.severity || 'HIGH',
        action: threat.immediateAction || 'Isolated endpoint & contained threat',
      };
      setActionLog(prev => [entry, ...prev].slice(0, 30));
    });

    const unsubCommands = subscribe('commands', (msg: any) => {
      const entry: CommandEntry = {
        commandId: msg.commandId || msg.id || 'CMD-UNKNOWN',
        deviceId: msg.deviceId || '',
        commandType: msg.commandType || msg.type || 'EXECUTE',
        target: msg.target,
        status: msg.status || 'COMPLETED',
        timestamp: new Date().toLocaleTimeString('en-US', { hour12: false }),
        result: msg.result || msg.details,
      };

      setCommandQueue(prev => {
        const existingIdx = prev.findIndex(c => c.commandId === entry.commandId);
        if (existingIdx >= 0) {
          const copy = [...prev];
          copy[existingIdx] = { ...copy[existingIdx], ...entry };
          return copy;
        }
        return [entry, ...prev].slice(0, 20);
      });
    });

    return () => {
      unsubThreats();
      unsubCommands();
    };
  }, [subscribe]);

  // ── Helper: Dispatch Direct Command ──
  const sendDirectCommand = async (commandType: string, target?: string) => {
    if (!targetDeviceId) {
      toast.error('No target device available. Ensure ASTRA Agent is running.');
      return;
    }
    if (!isTargetOnline) {
      toast.error(`TARGET OFFLINE — Device "${targetDevice?.name}" is not connected.`);
      return;
    }

    try {
      const payload: any = { commandType };
      if (target) payload.target = target;

      const res = await api.post(`/devices/${targetDeviceId}/command`, payload);
      const cmdId = res.data?.commandId || ('CMD-' + Date.now());

      setCommandQueue(prev => [
        {
          commandId: cmdId,
          deviceId: targetDeviceId,
          commandType,
          target,
          status: 'QUEUED',
          timestamp: new Date().toLocaleTimeString('en-US'),
        },
        ...prev.filter(c => c.commandId !== cmdId),
      ].slice(0, 20));

      toast.success(`Dispatched ${commandType} to ${targetDevice?.name}`);
    } catch (err: any) {
      toast.error(err.response?.data?.error || `Failed to dispatch ${commandType}`);
    }
  };

  // ── Helper: Trigger Live Attack ──
  const triggerLiveAttack = async (attackType: string) => {
    if (!targetDeviceId) {
      toast.error('No target device available. Connect ASTRA Agent.');
      return;
    }
    if (!isTargetOnline) {
      toast.error(`TARGET OFFLINE — Device "${targetDevice?.name}" is offline.`);
      return;
    }

    try {
      const targetParam = `?target=${encodeURIComponent(targetDeviceId)}`;
      const res = await api.post(`/live-attacks/${attackType}${targetParam}`);
      const cmdId = res.data?.commandId || ('CMD-' + Date.now());

      setCommandQueue(prev => [
        {
          commandId: cmdId,
          deviceId: targetDeviceId,
          commandType: attackType,
          target: attackType,
          status: 'QUEUED',
          timestamp: new Date().toLocaleTimeString('en-US'),
        },
        ...prev.filter(c => c.commandId !== cmdId),
      ].slice(0, 20));

      toast.success(`Dispatched ${attackType} on ${targetDevice?.name}`);
    } catch (err: any) {
      toast.error(err.response?.data?.error || 'TARGET OFFLINE — ACTION NOT EXECUTED');
    }
  };

  // ── Helper: Execute Dynamic Recovery from Scenario ──
  const executeScenarioRecovery = async (scenario: any) => {
    if (!targetDeviceId || !isTargetOnline) {
      toast.error('Target device offline or unavailable.');
      return;
    }

    const firstStep = scenario.dynamicRecovery?.[0];
    if (!firstStep?.script) {
      toast.info('No automated recovery script defined for this scenario.');
      return;
    }

    try {
      await api.post(`/devices/${targetDeviceId}/command`, {
        commandType: 'EXECUTE_DYNAMIC_SCRIPT',
        target: firstStep.script,
        parameters: JSON.stringify({ title: firstStep.name }),
      });
      toast.success(`Executed Recovery: ${firstStep.name}`);
    } catch (err: any) {
      toast.error('Failed to execute recovery step');
    }
  };

  // Filter 20 Scenarios
  const filteredScenarios = scenarios.filter((s: any) => {
    const matchesSearch = !searchQuery || 
      s.threatName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.category?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.mitreMapping?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesSeverity = severityFilter === 'ALL' || s.severity === severityFilter;
    return matchesSearch && matchesSeverity;
  });

  return (
    <PageContainer>
      <PageHeader 
        title="Threat Vectors & Endpoint Defense"
        description="Curated high-impact cyber threat demonstrations, verified safe enterprise scenarios, and deterministic on-target recovery."
      />

      {/* Target Device Selector Bar */}
      <div className="mb-6 p-4 border border-border-color/60 bg-surface/60 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="text-xs font-mono uppercase text-white/50 tracking-wider">Target Endpoint:</span>
          <select
            value={selectedDevice || (targetDevice?.id || '')}
            onChange={(e) => setSelectedDevice(e.target.value)}
            className="bg-surface border border-primary/40 px-3 py-1.5 text-sm text-white font-mono focus:outline-none focus:border-primary"
          >
            {devices.length === 0 ? (
              <option value="">No devices registered</option>
            ) : (
              devices.map((d: any) => (
                <option key={d.id} value={d.id}>
                  {d.name} ({String(d.status).toUpperCase()}) - {d.ipAddress || 'LAN'}
                </option>
              ))
            )}
          </select>
          {targetDevice && (
            <span className={`px-2 py-0.5 text-xs font-mono font-bold ${
              isTargetOnline 
                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40' 
                : 'bg-red-500/20 text-red-400 border border-red-500/40'
            }`}>
              {isTargetOnline ? '🟢 ONLINE' : '🔴 OFFLINE'}
            </span>
          )}
        </div>

        <Button 
          variant="outline" 
          size="sm" 
          onClick={() => refetchDevices()} 
          className="text-xs font-mono border-white/20 text-white/70 hover:text-white flex items-center gap-1.5"
        >
          <RefreshCw size={13} /> Refresh Endpoints
        </Button>
      </div>

      {!targetDevice ? (
        <div className="mb-6 p-3 border border-yellow-500/40 bg-yellow-500/10 text-yellow-400 text-xs font-mono flex items-center gap-2">
          <AlertTriangle size={18} className="shrink-0" />
          <span>NO TARGET SELECTED — Select an active endpoint from the dropdown above.</span>
        </div>
      ) : !isTargetOnline ? (
        <div className="mb-6 p-3 border border-danger/40 bg-danger/10 text-danger text-xs font-mono flex items-center gap-2">
          <ShieldAlert size={18} className="shrink-0" />
          <span>TARGET OFFLINE — Device "{targetDevice.name}" is offline. Ensure ASTRA Agent is running on that machine.</span>
        </div>
      ) : null}

      {/* Main Tab Navigation */}
      <div className="flex gap-2 mb-6 border-b border-border-color/50 pb-2 overflow-x-auto">
        <button
          onClick={() => setActiveTab('VISUAL_10')}
          className={`font-mono text-sm px-4 py-2 flex items-center gap-2 transition-all ${
            activeTab === 'VISUAL_10' 
              ? 'text-primary border-b-2 border-primary font-bold bg-primary/10 shadow-[0_0_15px_rgba(5,217,232,0.15)]' 
              : 'text-white/60 hover:text-white'
          }`}
        >
          <Eye size={16} className="text-primary" />
          10 FLAGSHIP VISUAL "WOW" THREATS
        </button>
        <button
          onClick={() => setActiveTab('REAL_20')}
          className={`font-mono text-sm px-4 py-2 flex items-center gap-2 transition-all ${
            activeTab === 'REAL_20' 
              ? 'text-danger border-b-2 border-danger font-bold bg-danger/10 shadow-[0_0_15px_rgba(255,61,113,0.15)]' 
              : 'text-white/60 hover:text-white'
          }`}
        >
          <Zap size={16} className="text-danger" />
          20 REAL-WORLD ENTERPRISE THREATS
        </button>
        <button
          onClick={() => setActiveTab('CONTROLS')}
          className={`font-mono text-sm px-4 py-2 flex items-center gap-2 transition-all ${
            activeTab === 'CONTROLS' 
              ? 'text-emerald-400 border-b-2 border-emerald-400 font-bold bg-emerald-500/10' 
              : 'text-white/60 hover:text-white'
          }`}
        >
          <ShieldCheck size={16} className="text-emerald-400" />
          DIRECT ENDPOINT CONTROLS
        </button>
      </div>

      {/* ── Main Operations Section ── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Left 2 Columns: Threat Catalog */}
        <div className="lg:col-span-2 space-y-6">

          {/* TAB 1: 10 FLAGSHIP VISUAL WOW THREATS */}
          {activeTab === 'VISUAL_10' && (
            <div className="space-y-4">
              <div className="p-3 border border-primary/30 bg-primary/5 rounded-none flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-mono font-bold text-primary flex items-center gap-2">
                    <Eye size={16} /> 10 PERFECT VISUAL THREAT DEMONSTRATIONS (ON-TARGET SCREEN)
                  </h3>
                  <p className="text-xs text-white/60 font-mono mt-0.5">
                    Each card triggers an immediate visual effect on the target laptop screen and provides an instant 1-click visual recovery.
                  </p>
                </div>
                <Badge variant="default">10 / 10 Active</Badge>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {VISUAL_THREATS_10.map((vt) => (
                  <div 
                    key={vt.id}
                    className="p-3.5 bg-surface/70 border border-border-color hover:border-primary/50 transition-all flex flex-col justify-between space-y-3"
                  >
                    <div>
                      <div className="flex items-start justify-between gap-2 mb-1">
                        <div className="flex items-center gap-2">
                          <span className="text-xl">{vt.icon}</span>
                          <h4 className="text-sm font-bold text-white font-mono">{vt.title}</h4>
                        </div>
                        <span className={`px-1.5 py-0.5 text-[10px] font-mono font-black border rounded-none ${severityClass(vt.severity)}`}>
                          {vt.severity}
                        </span>
                      </div>
                      
                      <p className="text-xs text-white/60 font-mono mt-1 mb-2">
                        {vt.description}
                      </p>

                      <div className="p-2 bg-black/40 border border-white/5 text-[11px] text-cyan-300/80 font-mono">
                        <span className="text-white/40 block text-[9px] uppercase tracking-wider">Visual Laptop Effect:</span>
                        {vt.laptopEffect}
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-2 pt-2 border-t border-white/5">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!isTargetOnline}
                        onClick={() => {
                          if (vt.attackType === 'LOCK_WORKSTATION') {
                            sendDirectCommand('LOCK_WORKSTATION', 'Incident Demonstration Lock');
                          } else {
                            triggerLiveAttack(vt.attackType);
                          }
                        }}
                        className="border-red-500/40 text-red-400 hover:bg-red-500/15 text-xs font-mono flex items-center justify-center gap-1.5"
                      >
                        <Zap size={13} /> Trigger Attack
                      </Button>

                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!isTargetOnline}
                        onClick={() => sendDirectCommand(vt.recoveryCommand, vt.recoveryTarget)}
                        className="border-emerald-500/40 text-emerald-400 hover:bg-emerald-500/15 text-xs font-mono flex items-center justify-center gap-1.5"
                      >
                        <CheckCircle size={13} /> Visual Recovery
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 2: 20 REAL-WORLD ENTERPRISE THREATS */}
          {activeTab === 'REAL_20' && (
            <div className="space-y-4">
              <div className="p-3 border border-danger/30 bg-danger/5 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-mono font-bold text-danger flex items-center gap-2">
                    <Zap size={16} /> 20 CURATED REAL-WORLD SAFE ENTERPRISE THREATS
                  </h3>
                  <p className="text-xs text-white/60 font-mono mt-0.5">
                    Safe, verified MITRE ATT&CK techniques executing in the target laptop sandbox without harming core OS files.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search size={14} className="absolute left-2.5 top-2.5 text-white/40" />
                    <input
                      type="text"
                      placeholder="Search 20 threats..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="bg-surface border border-border-color pl-8 pr-3 py-1 text-xs text-white font-mono focus:outline-none focus:border-primary"
                    />
                  </div>

                  <select
                    value={severityFilter}
                    onChange={(e) => setSeverityFilter(e.target.value)}
                    className="bg-surface border border-border-color px-2 py-1 text-xs text-white font-mono focus:outline-none focus:border-primary"
                  >
                    <option value="ALL">All Severities</option>
                    <option value="CRITICAL">Critical</option>
                    <option value="HIGH">High</option>
                    <option value="MEDIUM">Medium</option>
                  </select>
                </div>
              </div>

              {isLoadingScenarios ? (
                <div className="p-12 text-center font-mono text-sm text-primary animate-pulse">
                  Loading 20 enterprise threat scenarios...
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {filteredScenarios.map((scenario: any) => (
                    <div 
                      key={scenario.threatId}
                      className="p-3 bg-surface/60 border border-border-color hover:border-danger/40 transition-all flex flex-col justify-between space-y-2.5"
                    >
                      <div>
                        <div className="flex items-start justify-between gap-2">
                          <div>
                            <span className="text-[10px] font-mono text-primary font-bold">
                              {scenario.mitreMapping} • {scenario.category}
                            </span>
                            <h4 className="text-xs font-bold text-white font-mono mt-0.5">{scenario.threatName}</h4>
                          </div>
                          <span className={`px-1.5 py-0.5 text-[9px] font-mono font-black border rounded-none ${severityClass(scenario.severity)}`}>
                            {scenario.severity}
                          </span>
                        </div>

                        <p className="text-[11px] text-white/60 font-mono mt-1.5 line-clamp-2">
                          {scenario.description}
                        </p>
                      </div>

                      <div className="grid grid-cols-2 gap-2 pt-2 border-t border-white/5">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!isTargetOnline}
                          onClick={async () => {
                            try {
                              const targetParam = targetDeviceId ? `?target=${encodeURIComponent(targetDeviceId)}` : '';
                              await api.post(`/simulation/trigger/${scenario.threatId}${targetParam}`);
                              toast.success(`Triggered: ${scenario.threatName}`);
                            } catch (err: any) {
                              toast.error('Failed to trigger scenario');
                            }
                          }}
                          className="border-red-500/40 text-red-400 hover:bg-red-500/15 text-xs font-mono py-1.5 flex items-center justify-center gap-1"
                        >
                          <Zap size={12} /> Launch Threat
                        </Button>

                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!isTargetOnline}
                          onClick={() => executeScenarioRecovery(scenario)}
                          className="border-primary/40 text-primary hover:bg-primary/15 text-xs font-mono py-1.5 flex items-center justify-center gap-1"
                        >
                          <CheckCircle size={12} /> 1-Click Recovery
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 3: DIRECT PHYSICAL ENDPOINT DEFENSE CONTROLS */}
          {activeTab === 'CONTROLS' && (
            <Card className="border-emerald-500/30 bg-emerald-500/5">
              <h3 className="mb-3 text-base font-bold text-emerald-400 border-b border-emerald-500/20 pb-2 flex items-center gap-2 font-mono">
                <ShieldCheck size={18} /> Physical Endpoint Controls & Hardening
              </h3>
              <p className="text-xs text-white/70 mb-4 font-mono">
                Dispatches real Windows operating system enforcement commands to modify endpoint security baselines.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <Button
                  variant="outline"
                  disabled={!isTargetOnline}
                  onClick={() => sendDirectCommand('RESTORE_FIREWALL')}
                  className="border-emerald-500/50 text-emerald-400 hover:bg-emerald-500/15 font-mono text-xs py-2.5 flex items-center justify-center gap-2"
                >
                  <ShieldCheck size={14} /> Enable Windows Firewall
                </Button>

                <Button
                  variant="outline"
                  disabled={!isTargetOnline}
                  onClick={() => sendDirectCommand('ENABLE_REALTIME')}
                  className="border-emerald-500/50 text-emerald-400 hover:bg-emerald-500/15 font-mono text-xs py-2.5 flex items-center justify-center gap-2"
                >
                  <ShieldCheck size={14} /> Enable Defender Realtime AV
                </Button>

                <Button
                  variant="outline"
                  disabled={!isTargetOnline}
                  onClick={() => sendDirectCommand('DISABLE_RDP')}
                  className="border-primary/50 text-primary hover:bg-primary/15 font-mono text-xs py-2.5 flex items-center justify-center gap-2"
                >
                  <Lock size={14} /> Disable Remote Desktop (RDP)
                </Button>

                <Button
                  variant="outline"
                  disabled={!isTargetOnline}
                  onClick={() => sendDirectCommand('FINAL_VERIFICATION')}
                  className="border-cyan-500/50 text-cyan-400 hover:bg-cyan-500/15 font-mono text-xs py-2.5 flex items-center justify-center gap-2"
                >
                  <CheckCircle size={14} /> Audit System Integrity Baseline
                </Button>
              </div>
            </Card>
          )}

        </div>

        {/* Right 1 Column: Real-time Execution Pipeline & Response Timeline */}
        <div className="space-y-6">

          {/* Real-time Command Pipeline Card */}
          <Card className="border-border-color">
            <div className="flex items-center justify-between mb-3 border-b border-border-color pb-2">
              <h3 className="text-xs font-mono font-bold text-white flex items-center gap-2">
                <Terminal size={14} className="text-primary" />
                Live Command Dispatch Pipeline
              </h3>
              {commandQueue.length > 0 && (
                <button
                  onClick={() => setCommandQueue([])}
                  className="text-[10px] text-white/40 hover:text-white flex items-center gap-1 font-mono"
                >
                  <Trash2 size={10} /> Clear
                </button>
              )}
            </div>

            <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
              {commandQueue.length === 0 ? (
                <p className="text-xs text-white/30 font-mono py-6 text-center">
                  No commands dispatched in current session.
                </p>
              ) : (
                commandQueue.map((cmd) => (
                  <div key={cmd.commandId} className="p-2 bg-surface border border-border-color text-xs font-mono space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="text-white font-semibold truncate max-w-[140px]">{cmd.commandType}</span>
                      <span className={`px-1.5 py-0.5 text-[9px] font-black rounded-none ${
                        cmd.status === 'COMPLETED' || cmd.status === 'VERIFIED'
                          ? 'bg-success/20 text-success border border-success/40'
                          : cmd.status === 'FAILED' || cmd.status === 'REJECTED'
                          ? 'bg-danger/20 text-danger border border-danger/40'
                          : 'bg-primary/20 text-primary border border-primary/40 animate-pulse'
                      }`}>
                        {cmd.status}
                      </span>
                    </div>

                    <div className="flex items-center justify-between text-[10px] text-white/40">
                      <span>{cmd.commandId.substring(0, 14)}</span>
                      <span>{cmd.timestamp}</span>
                    </div>

                    {cmd.result && (
                      <div className="mt-1 p-1 bg-black/40 text-[10px] text-white/70 border-l border-primary/50 overflow-x-auto whitespace-pre-wrap font-mono">
                        {cmd.result}
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </Card>

          {/* Response Timeline Card */}
          <Card className="border-border-color">
            <div className="flex items-center justify-between mb-3 border-b border-border-color pb-2">
              <h3 className="text-xs font-mono font-bold text-white flex items-center gap-2">
                <Zap size={14} className="text-primary" />
                Response Timeline ({actionLog.length})
              </h3>
              {actionLog.length > 0 && (
                <button
                  onClick={() => setActionLog([])}
                  className="text-[10px] text-white/40 hover:text-white font-mono"
                >
                  <Trash2 size={10} /> Clear
                </button>
              )}
            </div>

            <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
              {actionLog.length === 0 ? (
                <p className="text-xs text-white/30 font-mono py-8 text-center">
                  No threat events captured yet.
                </p>
              ) : (
                actionLog.map((entry) => (
                  <div key={entry.id} className="p-2 font-mono text-xs border-l-2 border-primary/40 bg-surface/50 space-y-1">
                    <div className="flex items-center justify-between text-[10px]">
                      <span className="text-primary/70">[{entry.time}]</span>
                      <span className={`px-1 py-0 text-[8px] font-black border ${severityClass(entry.severity)}`}>
                        {entry.severity}
                      </span>
                    </div>
                    <div className="text-white/80 font-bold truncate text-[11px]">{entry.threatName}</div>
                    <div className="text-[10px] text-white/50">{entry.action}</div>
                  </div>
                ))
              )}
              <div ref={logEndRef} />
            </div>
          </Card>

        </div>

      </div>
    </PageContainer>
  );
}
