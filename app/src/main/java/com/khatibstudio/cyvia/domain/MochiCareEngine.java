package com.khatibstudio.cyvia.domain;

import com.khatibstudio.cyvia.data.db.entity.DailyLog;
import com.khatibstudio.cyvia.data.model.Mood;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Dynamic care message engine that generates tailored, AI-like well-being advice
 * based on current hour of day, cycle day/phase, logged mood, and symptoms.
 *
 * Enforces:
 *  - Maximum 2 lines per message
 *  - High empathy and uniqueness
 *  - Absolutely no emojis
 */
public class MochiCareEngine {

    public static String generateCareMessage(DailyLog todayLog, Integer cycleDay, String phase) {
        int hour = LocalTime.now().getHour();
        int dayOfYear = LocalDate.now().getDayOfYear();

        // 1. Check physical symptoms first if logged today
        if (todayLog != null && todayLog.symptomIds != null && !todayLog.symptomIds.isEmpty()) {
            String s = todayLog.symptomIds;
            // Cramps (1) or Backache (4)
            if (s.contains("1") || s.contains("4")) {
                if (hour < 12) {
                    String[] msgs = {
                        "Cramps can make mornings heavy. Sip warm tea, keep a heating pad close, and move gently today.",
                        "Listen to your body this morning. If aches feel strong, take breaks and stay well hydrated.",
                        "Starting the day with physical aches is tough. Give yourself permission to take things slow and easy."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else if (hour < 17) {
                    String[] msgs = {
                        "Afternoon check-in: If cramps or backaches are persisting, take a quiet stretch or resting break.",
                        "Your comfort matters most right now. Try loosening tense muscles and drinking warm water.",
                        "Midday reminder: You do not have to push through pain. Rest your back and breathe deeply."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else {
                    String[] msgs = {
                        "As evening settles, wrap yourself in warmth. A hot shower or heating pad can soothe those cramps tonight.",
                        "You made it through a painful day. Let your lower back relax and get cozy before bed.",
                        "Tonight is all about gentle recovery. Rest your body and let soothing warmth ease the ache."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                }
            }
            // Headache (2)
            if (s.contains("2")) {
                if (hour < 17) {
                    String[] msgs = {
                        "Headaches can cloud your focus. Drink a large glass of water and avoid glaring screens if you can.",
                        "Be gentle with your eyes and temples today. A quiet routine and hydration work wonders."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else {
                    String[] msgs = {
                        "Dim the lights and give your mind a break tonight. Rest your eyes for a peaceful sleep.",
                        "Screen-free evening time can help clear that headache. Wishing you a soothing, quiet night."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                }
            }
            // Bloating (3)
            if (s.contains("3")) {
                String[] msgs = {
                    "Bloating is completely natural during hormonal shifts. Wear breathable layers and treat yourself with kindness.",
                    "Feeling heavy or bloated today is normal. Gentle walking or peppermint tea can help settle things.",
                    "Release any pressure to feel physically perfect right now. Comfort and gentle digestion come first."
                };
                return msgs[(dayOfYear + hour) % msgs.length];
            }
            // Fatigue (6), Insomnia (10), or Low energy (16)
            if (s.contains("6") || s.contains("10") || s.contains("16")) {
                if (hour < 17) {
                    String[] msgs = {
                        "Waking up tired means your body needs extra grace today. Cross off non-essential tasks without guilt.",
                        "Low energy calls for slow steps. Nourish yourself well and conserve your reserves today.",
                        "Afternoon slump hitting hard? Closing your eyes for five minutes can help reset your nervous system."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else {
                    String[] msgs = {
                        "Prepare for a restorative sleep tonight. Put away distractions and let your mind drift into calm.",
                        "If sleep was scarce lately, tonight is your time to unwind. Deep breathing can help ease your mind."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                }
            }
            // Nausea (7), Tender breasts (8), Hot flashes (11)
            String[] generalSymptomMsgs = {
                "Hormonal shifts can bring heightened physical sensitivity. Wear soft fabrics and keep cool water nearby.",
                "Your physical sensations are valid. Take slow, steady breaths and give yourself extra comfort today."
            };
            return generalSymptomMsgs[(dayOfYear + hour) % generalSymptomMsgs.length];
        }

        // 2. Check mood next if logged today
        if (todayLog != null && todayLog.mood != null) {
            Mood mood = todayLog.mood;
            if (mood == Mood.SAD || mood == Mood.ANXIOUS || mood == Mood.IRRITABLE) {
                if (hour < 12) {
                    String[] msgs = {
                        "Emotional waves can feel heavy in the morning. Remember that every emotion is temporary and valid.",
                        "Take a deep, grounding breath as you start your day. You do not have to carry everything at once.",
                        "Be extra compassionate with yourself this morning. It is okay to feel unsettled or overwhelmed."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else if (hour < 17) {
                    String[] msgs = {
                        "Midday check-in: If tension is building, step outside or listen to calming music for a few minutes.",
                        "Your feelings matter. Take a break from heavy demands and protect your inner peace this afternoon."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else {
                    String[] msgs = {
                        "Let go of whatever felt stressful today. Tonight is a safe space to rest your heart and mind.",
                        "You handled today with courage. Exhale slowly and let calming quiet embrace you tonight."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                }
            } else if (mood == Mood.HAPPY || mood == Mood.CALM || mood == Mood.ENERGETIC) {
                if (hour < 12) {
                    String[] msgs = {
                        "Wonderful to see your positive outlook this morning! Channel this bright energy into what brings you joy.",
                        "Embrace the calm harmony in your morning. It is a beautiful day to connect with yourself and your goals."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else if (hour < 17) {
                    String[] msgs = {
                        "Your steady energy is a strength today. Enjoy this productive and balanced afternoon rhythm.",
                        "Savor this positive momentum! Keep nourishing your body so this vitality stays steady."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                } else {
                    String[] msgs = {
                        "What a gratifying day. Carry this calm, positive contentment into a deep and restful sleep tonight.",
                        "Ending the day on a bright note is special. Thank you for listening to and honoring your body."
                    };
                    return msgs[(dayOfYear + hour) % msgs.length];
                }
            } else if (mood == Mood.TIRED) {
                String[] msgs = {
                    "Feeling tired is your body asking for replenishment. Permit yourself to rest whenever you can today.",
                    "Honor your fatigue today without self-judgment. Early rest tonight will help rebuild your energy."
                };
                return msgs[(dayOfYear + hour) % msgs.length];
            }
        }

        // 3. Fallback to dynamic time-of-day + cycle phase + cycle day advice
        int cDay = (cycleDay != null && cycleDay > 0) ? cycleDay : 1;
        if ("MENSTRUAL".equals(phase)) {
            if (hour < 12) {
                String[] msgs = {
                    "During your period, your body expends significant energy. Start slowly and keep warm.",
                    "Cycle Day " + cDay + " morning check-in: Warmth and hydration are your greatest allies right now.",
                    "Welcome to your cycle morning. Honor what your body needs and move at a gentler pace today."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            } else if (hour < 17) {
                String[] msgs = {
                    "Menstrual phase afternoons are ideal for pacing yourself. Take brief breaks whenever you feel drained.",
                    "Your body is resetting on Day " + cDay + ". Drink some warm tea or water and keep stress levels low."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            } else {
                String[] msgs = {
                    "Rest is the most healing medicine during your period. Get cozy and prioritize early sleep tonight.",
                    "Evening reminder on Day " + cDay + ": Let your lower abdomen and back relax completely before bed."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            }
        } else if ("FOLLICULAR".equals(phase)) {
            if (hour < 12) {
                String[] msgs = {
                    "Rising estrogen levels often spark fresh morning focus. Wishing you an inspired start to Day " + cDay + "!",
                    "Your body is entering a phase of renewing vitality and biological strength."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            } else if (hour < 17) {
                String[] msgs = {
                    "Follicular afternoons bring natural resilience. Great time to tackle creative tasks or active movement.",
                    "Feel that steady rebuild of energy on Day " + cDay + ". Remember to stay fueled with nutritious meals."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            } else {
                String[] msgs = {
                    "As evening arrives, enjoy the lighter emotional balance of your follicular phase.",
                    "Unwind tonight knowing your biological vitality is steadily growing on Day " + cDay + "."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            }
        } else if ("OVULATORY".equals(phase)) {
            if (hour < 17) {
                String[] msgs = {
                    "You are around your fertile window on Day " + cDay + ", when biological energy and confidence often peak.",
                    "Hormones are supporting stamina and vibrancy today. Enjoy your natural biological strength!"
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            } else {
                String[] msgs = {
                    "Even during high-energy ovulatory days, a balanced evening routine keeps your sleep deep and sound.",
                    "Peak cycle vitality tonight. Stay well hydrated and enjoy a peaceful, winding-down evening."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            }
        } else {
            // LUTEAL phase or default
            if (hour < 12) {
                String[] msgs = {
                    "Progesterone on Day " + cDay + " can naturally invite a quieter, slower start to your day.",
                    "During the luteal phase, self-care is vital. Nourish your body with warm, sustaining breakfast choices."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            } else if (hour < 17) {
                String[] msgs = {
                    "Late-cycle afternoons can sometimes feel draining. Take a quiet moment to breathe and reset.",
                    "Listen closely to your body on Day " + cDay + ". If cravings or fatigue arise, respond with kindness."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            } else {
                String[] msgs = {
                    "Your body is winding down towards its next cycle. Create a peaceful, low-stimulation bedtime tonight.",
                    "Let go of expectations tonight. A warm bath or quiet reading can soothe late-cycle tension."
                };
                return msgs[(dayOfYear + cDay + hour) % msgs.length];
            }
        }
    }
}
