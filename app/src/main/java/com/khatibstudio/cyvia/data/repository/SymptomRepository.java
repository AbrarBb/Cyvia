package com.khatibstudio.cyvia.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.khatibstudio.cyvia.data.db.CyviaDatabase;
import com.khatibstudio.cyvia.data.db.dao.SymptomTagDao;
import com.khatibstudio.cyvia.data.db.entity.SymptomTag;
import com.khatibstudio.cyvia.data.model.SymptomCategory;

import java.util.List;

/**
 * Repository for symptom tag data.
 */
public class SymptomRepository {

    private final SymptomTagDao dao;

    public SymptomRepository(Application application) {
        dao = CyviaDatabase.getDatabase(application).symptomTagDao();
    }

    public LiveData<List<SymptomTag>> getAllSymptomTags() {
        return dao.getAllSymptomTags();
    }

    public LiveData<List<SymptomTag>> getCustomSymptomTags() {
        return dao.getCustomSymptomTags();
    }

    public List<SymptomTag> getAllSymptomTagsSync() {
        return dao.getAllSymptomTagsSync();
    }

    public List<SymptomTag> getSymptomTagsByIds(List<Integer> ids) {
        return dao.getSymptomTagsByIds(ids);
    }

    public void addCustomSymptom(String label, SymptomCategory category, String iconKey) {
        CyviaDatabase.databaseWriteExecutor.execute(() -> {
            dao.insertSymptomTag(SymptomTag.customSymptom(label, category, iconKey));
        });
    }

    public void addCustomSymptom(String label, SymptomCategory category) {
        addCustomSymptom(label, category, null);
    }

    public void deleteSymptomTag(SymptomTag tag) {
        if (tag.isCustom) {
            CyviaDatabase.databaseWriteExecutor.execute(() -> dao.deleteSymptomTag(tag));
        }
        // Silently ignore attempts to delete built-in symptoms
    }

    public void deleteAll() {
        CyviaDatabase.databaseWriteExecutor.execute(dao::deleteAll);
    }
}
