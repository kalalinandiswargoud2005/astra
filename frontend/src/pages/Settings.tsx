import React from 'react';
import { Settings as SettingsIcon, Save } from 'lucide-react';
import { Card, Button, Input, Badge, PageContainer, PageHeader, PageSection } from '@/components/ui';
import api from '@/lib/api';
import { toast } from 'sonner';

export function Settings() {
  const [responseMode, setResponseMode] = React.useState<'AUTOMATED' | 'MANUAL'>(() => {
    return (localStorage.getItem('astra_response_mode') as 'AUTOMATED' | 'MANUAL') || 'AUTOMATED';
  });

  const handleModeChange = (mode: 'AUTOMATED' | 'MANUAL') => {
    setResponseMode(mode);
    localStorage.setItem('astra_response_mode', mode);
    if (mode === 'AUTOMATED') {
      toast.success('Response Mode updated to Fully Automated (Agentic AI Execution)');
    } else {
      toast.info('Response Mode updated to Manual (Human-in-the-Loop Confirmation)');
    }
  };

  return (
    <PageContainer>
      <PageHeader 
        title="Settings" 
        description="Configure your ASTRA environment and response modes."
      >
        <Button variant="primary" onClick={() => toast.success('Settings saved successfully')}>
          <Save size={16} className="mr-2" />
          Save Changes
        </Button>
      </PageHeader>

      {/* ── Threat Response & Recovery Execution Mode Section ── */}
      <PageSection>
      <Card className="border-primary/40 bg-gradient-to-r from-primary/5 via-surface to-accent/5">
        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between border-b border-border-color pb-4 mb-4">
          <div>
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-primary animate-pulse" />
              Threat Response & Mitigation Execution Mode
            </h3>
            <p className="text-xs text-white/60 mt-0.5">
              Select how Agentic AI handles incoming security incidents, hardware actions, and recovery workflows.
            </p>
          </div>
          <Badge variant={responseMode === 'AUTOMATED' ? 'success' : 'warning'} className="self-start md:self-auto text-xs px-3 py-1">
            Current: {responseMode === 'AUTOMATED' ? '🤖 Fully Automated' : '🛠️ Manual Confirmation'}
          </Badge>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Fully Automated Mode Button */}
          <button
            type="button"
            onClick={() => handleModeChange('AUTOMATED')}
            className={`p-5 rounded-xl border text-left transition-all relative overflow-hidden flex flex-col justify-between ${
              responseMode === 'AUTOMATED'
                ? 'border-primary bg-primary/10 shadow-[0_0_25px_rgba(0,229,255,0.2)] ring-2 ring-primary/40'
                : 'border-border-color bg-surface/50 hover:border-primary/40 hover:bg-primary/5'
            }`}
          >
            <div>
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2 text-primary font-bold text-base">
                  <span className="p-2 rounded-lg bg-primary/20">🤖</span>
                  Fully Automated Mode
                </div>
                {responseMode === 'AUTOMATED' && (
                  <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-primary text-black">
                    ACTIVE
                  </span>
                )}
              </div>
              <p className="text-xs text-white/70 leading-relaxed mb-3">
                Agentic AI autonomously detects threats, performs immediate hardware containment (e.g. Wi-Fi isolation), displays emergency display alerts, and auto-resolves recovery steps.
              </p>
            </div>
            <div className="text-[11px] font-mono text-primary/80 flex items-center gap-1 mt-2">
              ⚡ Zero Latency • Autonomous Agentic Execution
            </div>
          </button>

          {/* Manual Mode Button */}
          <button
            type="button"
            onClick={() => handleModeChange('MANUAL')}
            className={`p-5 rounded-xl border text-left transition-all relative overflow-hidden flex flex-col justify-between ${
              responseMode === 'MANUAL'
                ? 'border-warning bg-warning/10 shadow-[0_0_25px_rgba(255,193,7,0.2)] ring-2 ring-warning/40'
                : 'border-border-color bg-surface/50 hover:border-warning/40 hover:bg-warning/5'
            }`}
          >
            <div>
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2 text-warning font-bold text-base">
                  <span className="p-2 rounded-lg bg-warning/20">🛠️</span>
                  Manual Mode
                </div>
                {responseMode === 'MANUAL' && (
                  <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-warning text-black">
                    ACTIVE
                  </span>
                )}
              </div>
              <p className="text-xs text-white/70 leading-relaxed mb-3">
                Human-in-the-Loop Mode. Agentic AI detects incidents and alerts the admin, requiring manual step-by-step confirmation in the Recovery Wizard before resolving threats.
              </p>
            </div>
            <div className="text-[11px] font-mono text-warning/80 flex items-center gap-1 mt-2">
              🛡️ Admin Review Required • Manual Confirmation
            </div>
          </button>
        </div>
      </Card>
      </PageSection>

      <PageSection className="grid grid-cols-1 gap-6">
        <Card>
          <h3 className="mb-4 text-lg font-medium text-white border-b border-border-color pb-2">Notifications</h3>
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-white">Critical Alerts</p>
                <p className="text-xs text-white/50">Push and email notifications for high severity incidents.</p>
              </div>
              <input type="checkbox" defaultChecked className="h-4 w-4 rounded border-border-color bg-surface text-primary focus:ring-primary/50" />
            </div>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-white">Weekly Reports</p>
                <p className="text-xs text-white/50">Automated summary emails.</p>
              </div>
              <input type="checkbox" defaultChecked className="h-4 w-4 rounded border-border-color bg-surface text-primary focus:ring-primary/50" />
            </div>
          </div>
        </Card>
        
        <Card className="lg:col-span-2">
          <h3 className="mb-4 text-lg font-medium text-white border-b border-border-color pb-2">System Status</h3>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="flex flex-col items-center justify-center p-4 border border-border-color rounded-lg bg-surface/30">
              <span className="w-3 h-3 rounded-full bg-success animate-pulse mb-2 shadow-[0_0_8px_rgba(1,255,7,0.8)]" />
              <span className="text-sm font-mono text-white/70">Backend</span>
              <span className="text-xs font-bold text-success">ONLINE</span>
            </div>
            <div className="flex flex-col items-center justify-center p-4 border border-border-color rounded-lg bg-surface/30">
              <span className="w-3 h-3 rounded-full bg-success animate-pulse mb-2 shadow-[0_0_8px_rgba(1,255,7,0.8)]" />
              <span className="text-sm font-mono text-white/70">Database</span>
              <span className="text-xs font-bold text-success">ONLINE</span>
            </div>
            <div className="flex flex-col items-center justify-center p-4 border border-border-color rounded-lg bg-surface/30">
              <span className="w-3 h-3 rounded-full bg-success animate-pulse mb-2 shadow-[0_0_8px_rgba(1,255,7,0.8)]" />
              <span className="text-sm font-mono text-white/70">AI Engine</span>
              <span className="text-xs font-bold text-success">ONLINE</span>
            </div>
            <div className="flex flex-col items-center justify-center p-4 border border-border-color rounded-lg bg-surface/30">
              <span className="w-3 h-3 rounded-full bg-success animate-pulse mb-2 shadow-[0_0_8px_rgba(1,255,7,0.8)]" />
              <span className="text-sm font-mono text-white/70">WebSocket</span>
              <span className="text-xs font-bold text-success">CONNECTED</span>
            </div>
          </div>
        </Card>

      </PageSection>
    </PageContainer>
  );
}

