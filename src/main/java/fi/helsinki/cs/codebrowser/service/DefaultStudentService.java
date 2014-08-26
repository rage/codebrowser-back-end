package fi.helsinki.cs.codebrowser.service;

import com.fasterxml.jackson.databind.DeserializationFeature;

import fi.helsinki.cs.codebrowser.exception.NotFoundException;
import fi.helsinki.cs.codebrowser.model.Course;
import fi.helsinki.cs.codebrowser.model.Exercise;
import fi.helsinki.cs.codebrowser.model.Student;
import fi.helsinki.cs.codebrowser.model.TmcSubmission;
import fi.helsinki.cs.codebrowser.util.JsonMapper;
import fi.helsinki.cs.codebrowser.web.client.SnapshotApiRestTemplate;
import fi.helsinki.cs.codebrowser.web.client.TmcApiRestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class DefaultStudentService implements StudentService {

    @Autowired
    private TmcApiRestTemplate tmcRestTemplate;

    @Autowired
    private SnapshotApiRestTemplate snapshotRestTemplate;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private CourseService courseService;

    private final JsonMapper mapper = new JsonMapper();

    @PostConstruct
    private void initialise() {

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private Exercise getCourseExerciseById(final String courseId, final String exerciseId) throws IOException {

        final Collection<Exercise> exercises = exerciseService.findAllBy(courseId);

        // Find exercise with ID
        for (Exercise exercise : exercises) {
            if (exercise.getId().equals(exerciseId)) {
                return exercise;
            }
        }

        throw new NotFoundException();
    }

    private Collection<Student> studentsWithSubmissions(final Collection<Student> courseStudents,
                                                        final List<TmcSubmission> submissions) {

        final Set<String> submitters = new HashSet<>();

        for (TmcSubmission submission : submissions) {
            submitters.add(submission.getUserId());
        }

        final Collection<Student> students = new ArrayList<>();

        for (Student student : courseStudents) {
            if (submitters.contains(student.getPlainId())) {
                students.add(student);
            }
        }

        return students;
    }

    @Override
    public Collection<Student> findAll() throws IOException {

        final String json = snapshotRestTemplate.getForObject("#", String.class);

        return mapper.readValueToList(json, Student.class);
    }

    @Override
    public Collection<Student> findAllBy(final String courseId) throws IOException {

        final Course course = courseService.findBy(courseId);
        final String json = tmcRestTemplate.fetchJson(String.format("courses/%s/points.json", course.getPlainId()),
                                                      "api_version=7");

        return mapper.readSubElementValueToList(json, Student.class, "users");
    }

    @Override
    public Collection<Student> findAllBy(final String courseId, final String exerciseId) throws IOException {

        final Exercise exercise = getCourseExerciseById(courseId, exerciseId);

        final String json = tmcRestTemplate.fetchJson(String.format("exercises/%s.json", exercise.getPlainId()),
                                                      "api_version=7");
        final List<TmcSubmission> submissions = mapper.readSubElementValueToList(json, TmcSubmission.class, "submissions");

        final Collection<Student> courseStudents = findAllBy(courseId);

        return studentsWithSubmissions(courseStudents, submissions);
    }

    @Override
    public Student find(final String courseId, final String exerciseId, final String studentId) throws IOException {

        final Collection<Student> students = findAllBy(courseId, exerciseId);

        // Find student by ID
        for (Student student : students) {
            if (student.getId().equals(studentId)) {
                return student;
            }
        }

        throw new NotFoundException();
    }

    @Override
    public Student find(final String courseId, final String studentId) throws IOException {

        final Collection<Student> students = findAllBy(courseId);

        // Find student by ID
        for (Student student : students) {
            if (student.getId().equals(studentId)) {
                return student;
            }
        }

        throw new NotFoundException();
    }

    @Override
    public Student find(final String studentId) throws IOException {

        final String json = snapshotRestTemplate.getForObject("{studentId}", String.class, studentId);

        return mapper.readValue(json, Student.class);
    }
}
