import React from 'react';
import { motion } from 'framer-motion';

interface JarvisCoreProps {
    isSpeaking: boolean;
    isThinking: boolean;
    isListening: boolean;
    transcript: string;
}

export const JarvisCore: React.FC<JarvisCoreProps> = ({ isSpeaking, isThinking, isListening, transcript }) => {
    
    // Determine the primary core color based on state
    const coreColor = isSpeaking ? '#01FF07' : isThinking ? '#F3E600' : '#05D9E8';
    
    // Determine the animation speeds based on state
    const spinSpeed = isThinking ? '3s' : isSpeaking ? '5s' : '15s';
    const reverseSpinSpeed = isThinking ? '4s' : isSpeaking ? '6s' : '20s';

    return (
        <div className="relative w-full h-full flex flex-col items-center justify-center overflow-hidden">
            
            {/* Central Core Visualization */}
            <div className="relative w-[500px] h-[500px] flex items-center justify-center select-none scale-75 md:scale-100">
                
                {/* Outer Glow */}
                <motion.div 
                    animate={{ 
                        boxShadow: isSpeaking ? `0 0 100px 20px ${coreColor}40` : `0 0 40px 5px ${coreColor}20` 
                    }}
                    transition={{ repeat: Infinity, duration: 1.5, repeatType: "reverse" }}
                    className="absolute inset-0 rounded-full"
                />

                {/* Outer Ring with Ticks */}
                <svg className="absolute w-[480px] h-[480px] animate-spin" style={{ animationDuration: spinSpeed }}>
                    <circle cx="240" cy="240" r="235" fill="none" stroke="#05D9E8" strokeWidth="2" strokeOpacity="0.3" strokeDasharray="4 8" />
                    <circle cx="240" cy="240" r="230" fill="none" stroke="#05D9E8" strokeWidth="1" strokeOpacity="0.5" />
                    {/* Large segments */}
                    <path d="M 240 10 A 230 230 0 0 1 470 240" fill="none" stroke="#05D9E8" strokeWidth="6" strokeOpacity="0.8" />
                    <path d="M 10 240 A 230 230 0 0 1 240 10" fill="none" stroke="#05D9E8" strokeWidth="2" strokeOpacity="0.5" />
                    
                    {/* Ticks */}
                    {[...Array(60)].map((_, i) => (
                        <line 
                            key={i}
                            x1="240" y1="15" x2="240" y2="25"
                            stroke="#05D9E8" strokeWidth="2" strokeOpacity={i % 5 === 0 ? "0.8" : "0.3"}
                            transform={`rotate(${i * 6} 240 240)`}
                        />
                    ))}
                </svg>

                {/* Middle Ring Reverse Spin */}
                <svg className="absolute w-[380px] h-[380px] animate-spin" style={{ animationDuration: reverseSpinSpeed, animationDirection: 'reverse' }}>
                    <circle cx="190" cy="190" r="185" fill="none" stroke="#05D9E8" strokeWidth="10" strokeOpacity="0.2" />
                    
                    {/* Cyan thick segmented arcs */}
                    <path d="M 190 5 A 185 185 0 0 1 375 190" fill="none" stroke="#05D9E8" strokeWidth="15" strokeOpacity="0.7" />
                    <path d="M 190 375 A 185 185 0 0 1 5 190" fill="none" stroke="#05D9E8" strokeWidth="10" strokeOpacity="0.5" />
                    
                    {/* Orange/Yellow Accent Arc */}
                    <path d="M 35 105 A 185 185 0 0 0 5 190" fill="none" stroke="#F3E600" strokeWidth="12" strokeOpacity="0.9" />
                    
                    {/* Inner yellow dots on the accent */}
                    {[...Array(5)].map((_, i) => (
                        <circle key={i} cx="190" cy="20" r="3" fill="#F3E600" transform={`rotate(${-30 + i * 15} 190 190)`} />
                    ))}
                </svg>

                {/* Inner Complex Ring */}
                <svg className="absolute w-[280px] h-[280px] animate-spin" style={{ animationDuration: spinSpeed }}>
                    <circle cx="140" cy="140" r="135" fill="none" stroke={coreColor} strokeWidth="1" strokeOpacity="0.5" />
                    <circle cx="140" cy="140" r="120" fill="none" stroke={coreColor} strokeWidth="1" strokeOpacity="0.3" strokeDasharray="5 5" />
                    <path d="M 140 5 A 135 135 0 0 1 275 140" fill="none" stroke={coreColor} strokeWidth="3" strokeOpacity="0.8" />
                </svg>

                {/* Very Center Grid Pattern */}
                <div className="absolute w-[200px] h-[200px] rounded-full border border-primary/20 overflow-hidden opacity-30">
                     <div className="w-full h-full animate-spin" style={{ animationDuration: '30s', backgroundImage: 'radial-gradient(circle at center, transparent 40%, rgba(5,217,232,0.1) 100%), repeating-linear-gradient(0deg, transparent, transparent 10px, rgba(5,217,232,0.2) 10px, rgba(5,217,232,0.2) 11px), repeating-linear-gradient(90deg, transparent, transparent 10px, rgba(5,217,232,0.2) 10px, rgba(5,217,232,0.2) 11px)' }} />
                </div>

                {/* Central Text */}
                <div className="absolute flex items-center justify-center z-10">
                    <motion.h1 
                        animate={isSpeaking ? { scale: [1, 1.05, 1], opacity: [0.8, 1, 0.8] } : { scale: 1, opacity: 0.9 }}
                        transition={{ repeat: Infinity, duration: 1 }}
                        className="text-5xl font-black font-mono tracking-[0.3em] text-white text-glow shadow-primary"
                        style={{ textShadow: `0 0 20px ${coreColor}, 0 0 40px ${coreColor}` }}
                    >
                        A.S.T.R.A.
                    </motion.h1>
                </div>
            </div>

            {/* Transcript Display */}
            <div className="absolute bottom-20 left-0 w-full px-8 flex justify-center z-20">
                <div className="max-w-2xl text-center">
                    {transcript && (
                        <p className="text-xl md:text-2xl font-mono text-white/90 leading-relaxed text-glow">
                            {transcript}
                        </p>
                    )}
                    {isThinking && !transcript && (
                        <p className="text-xl md:text-2xl font-mono text-warning leading-relaxed animate-pulse">
                            Analyzing systems...
                        </p>
                    )}
                </div>
            </div>
            
            {/* Status Indicator */}
            <div className="absolute top-8 flex flex-col items-center gap-2 text-white/50 font-mono text-xs uppercase tracking-[0.2em]">
                {isSpeaking ? (
                   <span className="text-success animate-pulse">Speech Synthesis Active</span>
                ) : isListening ? (
                   <span className="text-primary animate-pulse">Acoustic Sensors Online</span>
                ) : (
                   <span>Standby</span>
                )}
                {isSpeaking && (
                    <span className="text-[10px] text-white/30">Say "Stop" to interrupt</span>
                )}
            </div>

        </div>
    );
};
