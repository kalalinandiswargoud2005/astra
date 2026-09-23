import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Laptop, Server, Smartphone, CheckCircle, XCircle, Settings2, Trash2, AlertTriangle, Eye, Usb, Zap, RotateCw, Download, RefreshCw, Wifi } from 'lucide-react';
import { Card, Table, TableHeader, TableRow, TableHead, TableBody, TableCell, Badge, Button, PageContainer, PageHeader, PageSection } from '@/components/ui';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '@/lib/api';
import { toast } from 'sonner';
import { motion, AnimatePresence } from 'framer-motion';
import { UsbDeployModal } from '@/components/UsbDeployModal';
import { useWebSocket } from '@/providers/WebSocketProvider';

const getDeviceIcon = (type: string) => {
  if (type === 'Server') return <Server size={18} className="text-primary" />;
  if (type === 'Laptop' || type === 'Desktop') return <Laptop size={18} className="text-secondary" />;
  return <Smartphone size={18} className="text-white/50" />;
};

export function Devices() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [isManageMode, setIsManageMode] = useState(false);
  const [deviceToDelete, setDeviceToDelete] = useState<any | null>(null);
  const [isUsbModalOpen, setIsUsbModalOpen] = useState(false);
  const [isUpdatingAll, setIsUpdatingAll] = useState(false);
  const [actionInProgress, setActionInProgress] = useState<Record<string, string>>({});

  const { data: devices = [], isLoading, isError, refetch } = useQuery({
    queryKey: ['devices'],
    queryFn: async () => {
      const res = await api.get('/devices');
      return res.data;
    }
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number | string) => {
      await api.delete(`/devices/${id}`);
    },
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['devices'] });
      toast.success('Device Unregistered Successfully', {
        description: 'Endpoint removed from Astra security registry.'
      });
      setDeviceToDelete(null);
    },
    onError: () => {
      toast.error('Failed to remove device', {
        description: 'Please check connection or backend status.'
      });
    }
  });

  const handleConfirmRemove = () => {
    if (deviceToDelete) {
      deleteMutation.mutate(deviceToDelete.id);
    }
  };

  const handleUpdateAllAgents = async () => {
    setIsUpdatingAll(true);
    try {
      const onlineDevices = devices.filter((d: any) => String(d.status).toUpperCase() === 'ONLINE');
      if (onlineDevices.length === 0) {
        toast.info('No Online Devices Found', {
          description: 'Start or connect an agent on your target laptop first.'
        });
        return;
      }
      for (const dev of onlineDevices) {
        await api.post(`/devices/${dev.id}/command`, {
          commandType: 'UPDATE_AGENT',
          target: 'LATEST_BINARY'
        }).catch(() => {});
      }
      toast.success('⚡ OTA Update Broadcasted', {
        description: `Update commands dispatched to ${onlineDevices.length} active endpoint(s).`
      });
    } catch (err: any) {
      toast.error('Update broadcast failed', {
        description: err.response?.data?.error || 'Unable to contact backend.'
      });
    } finally {
      setTimeout(() => setIsUpdatingAll(false), 2000);
    }
  };

  const handleSingleDeviceAction = async (e: React.MouseEvent, deviceId: string, action: 'update' | 'restart') => {
    e.stopPropagation();
    setActionInProgress(prev => ({ ...prev, [deviceId]: action }));
    try {
      const commandType = action === 'update' ? 'UPDATE_AGENT' : 'RESTART_AGENT';
      await api.post(`/devices/${deviceId}/command`, {
        commandType,
        target: action === 'update' ? 'LATEST_BINARY' : 'RESTART_SERVICE'
      });
      if (action === 'update') {
        toast.success('⚡ Agent OTA Update Dispatched', {
          description: 'Target laptop will download the latest features & restart.'
        });
      } else {
        toast.success('🔄 Agent Restart Dispatched', {
          description: 'Target laptop agent service is restarting.'
        });
      }
    } catch (err: any) {
      toast.error(`Action failed`, {
        description: err.response?.data?.error || 'Target offline or unreachable.'
      });
    } finally {
      setTimeout(() => {
        setActionInProgress(prev => {
          const next = { ...prev };
          delete next[deviceId];
          return next;
        });
      }, 2500);
    }
  };

  const handleDownloadJar = () => {
    const backendBase = api.defaults.baseURL || 'http://localhost:8080/api/v1';
    window.open(`${backendBase}/agent/binary/download`, '_blank');
    toast.info('⬇️ Downloading Agent Binary (windows-agent.jar)');
  };

  return (
    <PageContainer>
      <PageHeader 
        title="Active Nodes"
        description="Monitor uplink nodes, update agent features OTA, and dispatch commands across the perimeter."
      >
        <div className="flex flex-wrap items-center gap-3">
          <Button 
            variant="outline" 
            onClick={handleDownloadJar}
            className="flex items-center gap-2 font-mono text-xs uppercase hover:border-primary/60"
          >
            <Download size={14} className="text-primary" />
            Download JAR
          </Button>

          <Button 
            variant="outline" 
            onClick={handleUpdateAllAgents}
            disabled={isUpdatingAll}
            className="flex items-center gap-2 font-mono text-xs uppercase border-primary/50 text-primary hover:bg-primary/10 shadow-[0_0_15px_rgba(0,240,255,0.2)]"
          >
            <Zap size={14} className={isUpdatingAll ? "animate-spin text-amber-400" : "text-primary"} />
            {isUpdatingAll ? 'Broadcasting...' : 'Update All Agents (OTA)'}
          </Button>

          <Button 
            variant={isManageMode ? "danger" : "outline"} 
            onClick={() => setIsManageMode(!isManageMode)}
            className="flex items-center gap-2 font-mono text-xs uppercase"
          >
            <Settings2 size={16} />
            {isManageMode ? 'Exit Manage Mode' : 'Manage Devices'}
          </Button>

          <Button 
            variant="primary" 
            onClick={() => setIsUsbModalOpen(true)}
            className="flex items-center gap-2 shadow-[0_0_15px_rgba(0,240,255,0.3)] font-mono text-xs uppercase"
          >
            <Wifi size={15} />
            Enroll Device (Wi-Fi / USB)
          </Button>
        </div>
      </PageHeader>

      <PageSection>
        {isManageMode && (
          <motion.div 
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="mb-4 p-3 bg-danger/10 border border-danger/40 flex items-center justify-between font-mono text-xs text-danger shadow-[0_0_15px_rgba(255,42,109,0.2)]"
          >
            <div className="flex items-center gap-2">
              <AlertTriangle size={16} className="animate-pulse" />
              <span>MANAGE MODE ACTIVE — Select "Remove" on any device to unregister it from Astra Security.</span>
            </div>
            <button 
              onClick={() => setIsManageMode(false)}
              className="underline hover:text-white transition-colors cursor-pointer font-bold"
            >
              Done
            </button>
          </motion.div>
        )}

        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="font-mono uppercase tracking-widest">Device</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">OS</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">IP Address</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Agent</TableHead>
                <TableHead className="font-mono uppercase tracking-widest">Status</TableHead>
                <TableHead className="font-mono uppercase tracking-widest text-center">Health</TableHead>
                <TableHead className="text-right font-mono uppercase tracking-widest">Remote Operations</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <AnimatePresence>
                {isLoading ? (
                  <motion.tr initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <TableCell colSpan={7} className="text-center py-12">
                      <div className="flex flex-col items-center justify-center space-y-4 text-primary animate-pulse">
                        <div className="flex items-center space-x-2">
                          <div className="w-2 h-2 rounded-full bg-primary animate-ping" />
                          <div className="w-2 h-2 rounded-full bg-primary animate-ping delay-75" />
                          <div className="w-2 h-2 rounded-full bg-primary animate-ping delay-150" />
                        </div>
                        <span className="font-mono uppercase tracking-widest text-sm text-glow">Scanning Endpoints...</span>
                      </div>
                    </TableCell>
                  </motion.tr>
                ) : isError ? (
                  <motion.tr initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <TableCell colSpan={isManageMode ? 7 : 6} className="text-center py-8 text-danger font-mono uppercase tracking-widest bg-danger/5">
                      Failed to fetch device registry.
                    </TableCell>
                  </motion.tr>
                ) : devices.length === 0 ? (
                  <motion.tr initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <TableCell colSpan={isManageMode ? 7 : 6} className="text-center py-8 text-white/50 font-mono">
                      No devices connected.
                    </TableCell>
                  </motion.tr>
                ) : (
                  devices.map((device: any) => {
                    const isDeviceOnline = String(device.status).toUpperCase() === 'ONLINE';
                    const currentAction = actionInProgress[device.id];

                    return (
                      <motion.tr 
                        key={device.id}
                        initial={{ opacity: 0, y: -20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.9 }}
                        transition={{ duration: 0.3 }}
                        onClick={() => !isManageMode && navigate(`/devices/${device.name || device.id}`)}
                        className={`border-b border-border-color transition-colors hover:bg-white/5 ${!isManageMode ? 'cursor-pointer' : ''}`}
                      >
                        <TableCell>
                          <div className="flex items-center gap-3">
                            <div className="flex h-10 w-10 items-center justify-center rounded-none cyber-cut bg-surface border border-border-color group-hover:border-primary/50 transition-colors">
                              {getDeviceIcon(device.type)}
                            </div>
                            <div className="flex flex-col">
                              <span className="font-rajdhani font-bold text-white tracking-wide flex items-center gap-2">
                                {device.name}
                                <Eye size={12} className="text-primary/70 hover:text-primary opacity-0 group-hover:opacity-100 transition-opacity" />
                              </span>
                              <span className="text-xs text-white/50 font-mono">{device.type}</span>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell className="text-white/70 font-mono text-sm">{device.os || 'Unknown'}</TableCell>
                        <TableCell className="text-white/70 font-mono text-sm">{device.ipAddress || device.ip}</TableCell>
                        <TableCell className="text-white/70 font-mono text-sm">
                          <span className="px-2 py-0.5 bg-primary/10 border border-primary/30 text-primary text-xs rounded-none">
                            {device.agentVersion || 'v1.0.0'}
                          </span>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1.5 text-sm font-mono font-bold">
                            {isDeviceOnline ? (
                              <CheckCircle size={14} className="text-success" />
                            ) : (
                              <XCircle size={14} className="text-white/30" />
                            )}
                            <span className={isDeviceOnline ? 'text-success' : 'text-white/50'}>
                              {device.status}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell className="text-center">
                          <Badge variant={
                            device.health === 'Healthy' ? 'success' :
                            device.health === 'Warning' ? 'warning' : 'danger'
                          }>
                            {device.health}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          {isManageMode ? (
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                setDeviceToDelete(device);
                              }}
                              className="inline-flex items-center gap-1.5 px-3 py-1 bg-danger/20 hover:bg-danger/40 text-danger border border-danger/50 text-xs font-mono font-bold transition-all shadow-[0_0_10px_rgba(255,42,109,0.3)] cursor-pointer"
                            >
                              <Trash2 size={13} />
                              <span>Remove</span>
                            </button>
                          ) : (
                            <div className="flex items-center justify-end gap-2">
                              {isDeviceOnline && (
                                <>
                                  <button
                                    title="Download and deploy latest agent code OTA"
                                    onClick={(e) => handleSingleDeviceAction(e, device.id, 'update')}
                                    disabled={!!currentAction}
                                    className="inline-flex items-center gap-1 px-2.5 py-1 bg-primary/15 hover:bg-primary/30 text-primary border border-primary/40 text-xs font-mono font-bold transition-all cursor-pointer shadow-[0_0_8px_rgba(0,240,255,0.2)] disabled:opacity-50"
                                  >
                                    <Zap size={12} className={currentAction === 'update' ? 'animate-spin text-amber-400' : ''} />
                                    <span>{currentAction === 'update' ? 'Updating...' : 'Update'}</span>
                                  </button>

                                  <button
                                    title="Remotely restart agent process on target"
                                    onClick={(e) => handleSingleDeviceAction(e, device.id, 'restart')}
                                    disabled={!!currentAction}
                                    className="inline-flex items-center gap-1 px-2 py-1 bg-white/5 hover:bg-white/10 text-white/80 border border-white/20 text-xs font-mono font-bold transition-all cursor-pointer disabled:opacity-50"
                                  >
                                    <RotateCw size={12} className={currentAction === 'restart' ? 'animate-spin text-primary' : ''} />
                                    <span>{currentAction === 'restart' ? 'Restarting...' : 'Restart'}</span>
                                  </button>
                                </>
                              )}

                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  navigate(`/devices/${device.name || device.id}`);
                                }}
                                className="inline-flex items-center gap-1.5 px-3 py-1 bg-surface hover:bg-primary/20 text-primary border border-primary/40 text-xs font-mono font-bold transition-all cursor-pointer"
                              >
                                <Eye size={13} />
                                <span>Deck</span>
                              </button>
                            </div>
                          )}
                        </TableCell>
                      </motion.tr>
                    );
                  })
                )}
              </AnimatePresence>
            </TableBody>
          </Table>
        </Card>
      </PageSection>

      {/* Confirmation Modal */}
      {deviceToDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
          <motion.div 
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className="w-full max-w-md bg-[#020617] border border-danger/60 p-6 font-mono shadow-[0_0_30px_rgba(255,42,109,0.4)]"
          >
            <div className="flex items-center gap-3 text-danger font-bold text-lg border-b border-danger/30 pb-3 mb-4">
              <AlertTriangle size={24} />
              <span>CONFIRM DEVICE REMOVAL</span>
            </div>

            <p className="text-sm text-white/80 mb-2">
              Are you sure you want to remove <span className="text-danger font-bold">{deviceToDelete.name}</span> ({deviceToDelete.ipAddress || deviceToDelete.ip}) from Astra Security Registry?
            </p>
            <p className="text-xs text-white/50 mb-6">
              This action will unregister the endpoint agent and revoke C2 connection keys.
            </p>

            <div className="flex items-center justify-end gap-3">
              <Button 
                variant="outline" 
                onClick={() => setDeviceToDelete(null)}
                disabled={deleteMutation.isPending}
              >
                Cancel
              </Button>

              <Button 
                variant="danger" 
                onClick={handleConfirmRemove}
                disabled={deleteMutation.isPending}
                className="flex items-center gap-2"
              >
                <Trash2 size={16} />
                {deleteMutation.isPending ? 'Removing...' : 'Confirm Remove'}
              </Button>
            </div>
          </motion.div>
        </div>
      )}
      {/* USB Agent Deployment Modal */}
      <UsbDeployModal 
        isOpen={isUsbModalOpen} 
        onClose={() => setIsUsbModalOpen(false)} 
      />
    </PageContainer>
  );
}
